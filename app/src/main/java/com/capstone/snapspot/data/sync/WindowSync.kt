package com.capstone.snapspot.data.sync

import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.RegionRegistrar
import com.capstone.snapspot.domain.WindowSummary
import com.capstone.snapspot.domain.buildWindow
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Region
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-plans which spots to watch around the user, remembers the plan so the map, the coupon wallet and the
 * status screen can show it, and registers it with the OS so alerts come with the app closed.
 */
@Singleton
class WindowSync @Inject constructor(
    private val places: PlaceRepository,
    private val coupons: CouponRepository,
    private val windows: WindowRepository,
    private val events: EventLogRepository,
    private val kakao: KakaoLocalApi,
    private val cache: BranchCache,
    private val registrar: RegionRegistrar,
) {
    // Two re-plans at once would race on the saved plan and the log.
    private val lock = Mutex()

    suspend fun sync(here: Coords?, now: Long = System.currentTimeMillis()): WindowSummary = lock.withLock {
        val built = buildWindow(places.getAll(), coupons.getAll(), here, kakao, cache, now)
        windows.save(built.state)
        events.append(
            LogEvent(
                t = now,
                kind = EventKind.REPLAN,
                detail = mapOf("active" to built.summary.active, "requests" to built.summary.requests, "located" to (here != null)),
            ),
        )
        register(built.plan.regions)
        built.summary
    }

    /** The saved plan and the log are already written, so a registration problem (location off, no access) is not fatal. */
    private suspend fun register(regions: List<Region>) {
        try {
            if (regions.isEmpty()) registrar.clear() else registrar.register(regions)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next re-plan registers again.
        }
    }
}
