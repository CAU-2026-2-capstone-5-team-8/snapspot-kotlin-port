package com.capstone.snapspot.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.snapspot.data.image.AndroidImageSource
import com.capstone.snapspot.data.location.LocationSource
import com.capstone.snapspot.data.net.ApiKeys
import com.capstone.snapspot.data.repo.RoomSavePort
import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.ScreenshotAnalyzer
import com.capstone.snapspot.domain.draft.AddFlow
import com.capstone.snapspot.domain.draft.Draft
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Kind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** A thin wrapper that gives [AddFlow] a lifetime: the tabs and the add screen share one. */
@HiltViewModel
class AddViewModel @Inject constructor(
    images: AndroidImageSource,
    analyzer: ScreenshotAnalyzer,
    kakao: KakaoLocalApi,
    geocoder: GeocodePort,
    branchCache: BranchCache,
    private val location: LocationSource,
    savePort: RoomSavePort,
    keys: ApiKeys,
) : ViewModel() {
    private val flow = AddFlow(
        scope = viewModelScope,
        images = images,
        analyzer = analyzer,
        // No Kakao key: skip Kakao and rely on the device geocoder.
        kakao = if (keys.kakaoRestKey.isBlank()) null else kakao,
        geocoder = geocoder,
        branchCache = branchCache,
        here = location,
        store = savePort,
    )

    val draft: StateFlow<Draft?> get() = flow.draft
    val busy: StateFlow<Boolean> get() = flow.busy
    val events get() = flow.events
    val here: StateFlow<Coords?> get() = location.here

    /** False when the analyzer cannot work yet; the screen explains what to set up. */
    val analyzerConfigured: Boolean = keys.geminiKey.isNotBlank()

    fun start(uri: String) = flow.start(uri)

    fun cancel() = flow.cancel()

    suspend fun save() = flow.save()

    fun setKind(kind: Kind) = flow.setKind(kind)

    fun update(patch: (Draft) -> Draft) = flow.update(patch)

    fun regeocode() = flow.regeocode()

    fun pinToHere() = flow.pinToHere()

    fun pickCandidate(c: Candidate) = flow.pickCandidate(c)

    fun refreshBranchPreview() = flow.refreshBranchPreview()
}
