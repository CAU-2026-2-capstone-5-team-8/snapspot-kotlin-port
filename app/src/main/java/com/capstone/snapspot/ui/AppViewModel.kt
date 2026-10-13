package com.capstone.snapspot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.snapspot.data.image.ImageStore
import com.capstone.snapspot.data.location.LocationSource
import com.capstone.snapspot.data.notify.AlertCenter
import com.capstone.snapspot.data.permissions.BatteryHint
import com.capstone.snapspot.data.permissions.DeviceLocation
import com.capstone.snapspot.data.permissions.LocationSettingsChecker
import com.capstone.snapspot.data.permissions.PermissionPrefs
import com.capstone.snapspot.data.permissions.PermissionReader
import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.data.sync.WindowSync
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.domain.PermissionStep
import com.capstone.snapspot.domain.isGranted
import com.capstone.snapspot.domain.draft.Saved
import com.capstone.snapspot.domain.isActive
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.domain.placesToAlert
import com.capstone.snapspot.domain.sortedByDistance
import com.capstone.snapspot.domain.testBranchId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** What every screen needs: the saved places and coupons, the watch plan, where the user is, and the actions on them. */
@OptIn(FlowPreview::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val couponRepo: CouponRepository,
    private val windows: WindowRepository,
    private val events: EventLogRepository,
    private val location: LocationSource,
    private val images: ImageStore,
    private val sync: WindowSync,
    private val permissionReader: PermissionReader,
    private val alerts: AlertCenter,
    private val permissionPrefs: PermissionPrefs,
    private val locationSettings: LocationSettingsChecker,
    private val battery: BatteryHint,
) : ViewModel() {
    val here: StateFlow<Coords?> get() = location.here

    /** Nearest first once the position is known. */
    val places: StateFlow<List<Place>> =
        combine(placeRepo.observeAll(), location.here) { list, at -> sortedByDistance(list, at) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val coupons: StateFlow<List<Coupon>> =
        couponRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The plan currently being watched: coupon branches, slot split, last re-plan. */
    val win: StateFlow<WindowState?> =
        windows.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val eventCount: StateFlow<Int> =
        events.observeAll().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _perms = MutableStateFlow(permissionReader.read())
    val perms: StateFlow<AlertPermissions> = _perms

    /** Steps the user refused before; a refused pop-up may never show again, so these go to settings. */
    val deniedSteps: StateFlow<Set<PermissionStep>> =
        permissionPrefs.denied.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Null until stored state is read, so the first-launch guide does not open on a guess. */
    val guideSeen: StateFlow<Boolean?> =
        permissionPrefs.guideSeen.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _deviceLocation = MutableStateFlow(DeviceLocation(on = true))

    /** The phone's own location switch. Unknown counts as on until checked. */
    val deviceLocation: StateFlow<DeviceLocation> = _deviceLocation

    private val _batteryRestricted = MutableStateFlow(false)
    val batteryRestricted: StateFlow<Boolean> = _batteryRestricted

    private val _justSavedId = MutableStateFlow<String?>(null)

    /** The place saved last, so the map can show where it landed. */
    val justSavedId: StateFlow<String?> = _justSavedId

    private var started = false

    init {
        // Re-plan when what is watched changes: a place or active coupon was added or removed, the position first
        // became known, or "always" location access was just granted (until then nothing can be registered).
        viewModelScope.launch {
            combine(
                placeRepo.observeAll().map { it.size }.distinctUntilChanged(),
                couponRepo.observeAll().map { list -> list.count { isActive(it) } }.distinctUntilChanged(),
                location.here.map { it != null }.distinctUntilChanged(),
                _perms.map { it.bg }.distinctUntilChanged(),
            ) { _, _, _, _ -> }
                .debounce(300)
                .collect {
                    try {
                        sync.sync(location.here.value)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A failed re-plan keeps the previous plan; the next change tries again.
                    }
                }
        }
    }

    /** Once per launch: find the user, and clear images that no saved item uses (a draft lost to a kill). */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            location.refresh()
            val used = placeRepo.getAll().map { it.imageUri } + couponRepo.getAll().map { it.imageUri }
            images.sweep(used.toSet())
        }
    }

    /**
     * The user came back from a pop-up or from settings for [step]: re-read the permissions and remember whether it
     * was refused. Location starts working as soon as plain location is granted.
     */
    fun onPermissionResult(step: PermissionStep) {
        val read = permissionReader.read()
        _perms.value = read
        val granted = isGranted(step, read)
        viewModelScope.launch { permissionPrefs.setDenied(step, denied = !granted) }
        if (read.fg) {
            startWatching()
            viewModelScope.launch { location.refresh() }
        }
    }

    fun markGuideSeen() {
        viewModelScope.launch { permissionPrefs.markGuideSeen() }
    }

    /** Whatever the user changed outside the app (settings, quick tiles) shows up when they come back. */
    fun refreshEnvironment() {
        refreshPermissions()
        _batteryRestricted.value = battery.isRestricted()
        viewModelScope.launch {
            // Granted in settings after a refusal: that step is no longer refused.
            for (step in deniedSteps.value) if (isGranted(step, _perms.value)) permissionPrefs.setDenied(step, denied = false)
            _deviceLocation.value = locationSettings.check()
            if (_perms.value.fg && location.here.value == null) location.refresh()
        }
    }

    private var watchJob: Job? = null
    private var visible = false

    /** The app came to the front: follow the user, so distances stay live and arrivals are caught right away. */
    fun onForeground() {
        visible = true
        startWatching()
    }

    /** Not visible any more: stop following; the OS geofences carry on. */
    fun onBackground() {
        visible = false
        watchJob?.cancel()
        watchJob = null
    }

    private fun startWatching() {
        if (!visible || watchJob != null || !location.hasPermission()) return
        val job = viewModelScope.launch {
            location.watch().collect { at ->
                location.update(at)
                for (p in placesToAlert(placeRepo.getAll(), at)) alerts.notifyPlace(p.id)
            }
        }
        watchJob = job
        job.invokeOnCompletion { if (watchJob === job) watchJob = null }
    }

    /** "알림 테스트" on a place: shows the alert now, ignoring the cooldown. */
    fun testPlaceAlert(p: Place) {
        viewModelScope.launch { alerts.notifyPlace(p.id, force = true) }
    }

    /** "알림 테스트" on a coupon: shows the alert its nearest branch would send, ignoring the cooldown. */
    fun testCouponAlert(c: Coupon) {
        viewModelScope.launch { alerts.notifyBranch(testBranchId(c, win.value, location.here.value), force = true) }
    }

    /** A place alert was tapped: the map selects that place. */
    fun showOnMap(placeId: String) {
        _justSavedId.value = placeId
    }

    /** Coming back from system settings can change the permissions, so screens call this on resume. */
    fun refreshPermissions() {
        _perms.value = permissionReader.read()
    }

    fun onSaved(saved: Saved) {
        if (saved.kind == Kind.PLACE) _justSavedId.value = saved.id
    }

    fun clearJustSaved() {
        _justSavedId.value = null
    }

    fun deletePlace(p: Place) {
        viewModelScope.launch {
            placeRepo.remove(p.id)
            images.delete(p.imageUri)
        }
    }

    fun deleteCoupon(c: Coupon) {
        viewModelScope.launch {
            couponRepo.remove(c.id)
            images.delete(c.imageUri)
        }
    }

    fun markCouponUsed(c: Coupon) {
        viewModelScope.launch { couponRepo.markUsed(c.id, System.currentTimeMillis()) }
    }

    /** The whole event log as JSON, in the shape the React Native app exported. */
    suspend fun exportLogJson(): String = events.exportJson()

    fun clearLog() {
        viewModelScope.launch { events.clear() }
    }
}
