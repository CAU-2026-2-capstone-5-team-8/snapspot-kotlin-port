package com.capstone.snapspot.domain.draft

import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.Located
import com.capstone.snapspot.domain.ScreenshotAnalyzer
import com.capstone.snapspot.domain.locatePlace
import com.capstone.snapspot.domain.looksLikeDate
import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.previewBranches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adding a place or coupon from a screenshot: pick, read, find on the map, confirm, save.
 * One draft at a time. Work runs in [scope]; a newer pick replaces an older one, and results from the
 * older one are dropped.
 *
 * @param kakao null when there is no Kakao key; locating then relies on the device geocoder.
 */
class AddFlow(
    private val scope: CoroutineScope,
    private val images: ImageSource,
    private val analyzer: ScreenshotAnalyzer,
    private val kakao: KakaoLocalApi?,
    private val geocoder: GeocodePort,
    private val branchCache: BranchCache,
    private val here: HerePort,
    private val store: SavePort,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    /** True while "find again" runs. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<AddEvent>(Channel.UNLIMITED)
    val events: Flow<AddEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    /** Counts picks, so slow work from an earlier pick can tell it was replaced. */
    private var generation = 0

    /** The copy of the picked image in app storage, until it is saved with a place or dropped. */
    private var ownedImage: String? = null

    fun start(uri: String) {
        job?.cancel()
        val gen = ++generation
        discardImage()
        _busy.value = false
        _draft.value = Draft(Stage.READING, imageUri = uri)
        job = scope.launch {
            try {
                val prepared = images.prepare(uri)
                if (gen != generation) {
                    images.delete(prepared.path)
                    return@launch
                }
                ownedImage = prepared.path
                _draft.update { it?.copy(imageUri = prepared.path) }

                val ex = analyzer.analyze(prepared.jpeg)
                if (gen != generation) return@launch
                _draft.value = Draft(
                    stage = Stage.LOCATING,
                    kind = ex.kind,
                    imageUri = prepared.path,
                    brand = ex.brand,
                    benefit = ex.benefit,
                    name = ex.name,
                    address = ex.address,
                    category = ex.category,
                    note = ex.note,
                    endDate = ex.endDate.orEmpty(),
                    isPlace = ex.isPlace,
                )

                if (ex.kind == Kind.COUPON) {
                    val branches = branchesFor(ex.brand)
                    if (gen == generation) _draft.update { it?.copy(branches = branches, stage = Stage.READY) }
                    return@launch
                }
                val located = if (ex.isPlace) locate(ex.name, ex.address, ex.category) else Located(null, null, emptyList())
                if (gen == generation) {
                    _draft.update { it?.copy(coords = located.coords, matched = located.matched, candidates = located.candidates, stage = Stage.READY) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    _draft.update { it?.copy(stage = Stage.READY) }
                    _events.trySend(AddEvent.ReadFailed(e.message ?: e.toString()))
                }
            }
        }
    }

    fun update(patch: (Draft) -> Draft) {
        _draft.update { it?.let(patch) }
    }

    /** Gemini can guess wrong, so the user can switch what the screenshot is. */
    fun setKind(kind: Kind) {
        val d = _draft.value ?: return
        if (d.kind == kind) return
        val gen = generation
        if (kind == Kind.COUPON) {
            val brand = d.brand.ifEmpty { d.name }
            _draft.update { it?.copy(kind = kind, brand = brand, branches = null) }
            scope.launch {
                val branches = branchesFor(brand)
                if (gen == generation) _draft.update { it?.copy(branches = branches) }
            }
        } else {
            _draft.update { it?.copy(kind = kind) }
            if (d.coords == null) {
                scope.launch {
                    val located = locate(d.name, d.address, d.category)
                    if (gen == generation) _draft.update { it?.copy(coords = located.coords, matched = located.matched, candidates = located.candidates) }
                }
            }
        }
    }

    /** "Find again by address": search the maps again with the edited name and address. */
    fun regeocode() {
        val d = _draft.value ?: return
        val gen = generation
        scope.launch {
            _busy.value = true
            try {
                val located = locate(d.name, d.address, d.category)
                if (gen != generation) return@launch
                _draft.update { it?.copy(coords = located.coords, matched = located.matched, candidates = located.candidates) }
                if (located.coords == null) _events.trySend(AddEvent.LocationNotFound)
            } finally {
                _busy.value = false
            }
        }
    }

    fun pickCandidate(c: Candidate) {
        _draft.update { d ->
            d?.copy(
                name = if (c.source == CandidateSource.KEYWORD) c.name else d.name,
                address = c.address,
                coords = com.capstone.snapspot.domain.model.Coords(c.latitude, c.longitude),
                matched = c,
            )
        }
    }

    /** "Use my current position" as the pin. */
    fun pinToHere() {
        val gen = generation
        scope.launch {
            val at = here.current() ?: return@launch
            if (gen == generation) _draft.update { it?.copy(coords = at, matched = null) }
        }
    }

    /** "Find again by brand" for the branch preview. */
    fun refreshBranchPreview() {
        val d = _draft.value ?: return
        val gen = generation
        scope.launch {
            _busy.value = true
            try {
                val branches = branchesFor(d.brand)
                if (gen == generation) _draft.update { it?.copy(branches = branches) }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Saves the draft as a place or a coupon. Null when it is not ready (no brand, or no pin). */
    suspend fun save(): Saved? {
        val d = _draft.value ?: return null
        val t = now()
        val endDate = d.endDate.takeIf { looksLikeDate(it) }
        val saved: Saved
        if (d.kind == Kind.COUPON) {
            if (d.brand.isBlank()) return null
            val coupon = Coupon(
                id = "c_${t.toString(36)}",
                brand = d.brand.trim(),
                benefit = d.benefit.trim().ifEmpty { "쿠폰" },
                expiresAt = endDate,
                imageUri = ownedImage,
                createdAt = t,
                usedAt = null,
                lastNotifiedAt = null,
            )
            store.saveCoupon(coupon)
            saved = Saved(Kind.COUPON, coupon.id)
        } else {
            val at = d.coords ?: return null
            val place = Place(
                id = "p_${t.toString(36)}",
                name = d.name.ifEmpty { "이름 없는 장소" },
                address = d.address,
                category = d.category.ifEmpty { "기타" },
                note = d.note,
                endDate = endDate,
                latitude = at.latitude,
                longitude = at.longitude,
                radius = d.radius,
                imageUri = ownedImage,
                kakaoId = d.matched?.kakaoId,
                placeUrl = d.matched?.placeUrl,
                createdAt = t,
                lastNotifiedAt = null,
            )
            store.savePlace(place)
            saved = Saved(Kind.PLACE, place.id)
        }
        job?.cancel()
        generation++
        ownedImage = null // the image now belongs to the saved item
        _draft.value = null
        return saved
    }

    /** Drops the draft and the image copy. */
    fun cancel() {
        job?.cancel()
        generation++
        _busy.value = false
        discardImage()
        _draft.value = null
    }

    private fun discardImage() {
        val path = ownedImage ?: return
        ownedImage = null
        // Runs to the end even if the flow's scope is being cancelled, so no file is left behind.
        scope.launch { withContext(NonCancellable) { images.delete(path) } }
    }

    private suspend fun locate(name: String, address: String, category: String): Located =
        locatePlace(name, address, category, kakao, geocoder)

    private suspend fun branchesFor(brand: String): List<Branch>? =
        if (kakao == null) null else previewBranches(brand, here.current(), kakao, branchCache)
}
