package com.capstone.snapspot.data.notify

import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.domain.Alert
import com.capstone.snapspot.domain.AlertSink
import com.capstone.snapspot.domain.BranchDecision
import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.SuppressReason
import com.capstone.snapspot.domain.couponAlert
import com.capstone.snapspot.domain.decideBranchAlert
import com.capstone.snapspot.domain.placeAlert
import com.capstone.snapspot.domain.suppressReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether an alert goes out, shows it, and records the result. Geofence events often arrive in pairs and
 * the foreground watcher can fire at the same moment, so check, show and mark happen one at a time: the second
 * caller sees the cooldown the first one started.
 */
@Singleton
class AlertCenter @Inject constructor(
    private val places: PlaceRepository,
    private val coupons: CouponRepository,
    private val windows: WindowRepository,
    private val events: EventLogRepository,
    private val sink: AlertSink,
) {
    private val lock = Mutex()

    /** [force] skips the cooldown and the ended check; it is the "알림 테스트" button. True if an alert was shown. */
    suspend fun notifyPlace(placeId: String, force: Boolean = false, now: Long = System.currentTimeMillis()): Boolean = lock.withLock {
        val place = places.get(placeId) ?: return@withLock false
        val reason = if (force) null else suppressReason(place, now)
        if (reason != null) {
            events.append(LogEvent(now, EventKind.NOTIFY_SUPPRESSED, place.id, mapOf("reason" to reason.label)))
            return@withLock false
        }
        if (!deliver(placeAlert(place), place.id, now)) return@withLock false
        places.markNotified(place.id, now)
        events.append(LogEvent(now, EventKind.NOTIFY_SHOWN, place.id, mapOf("type" to "place", "forced" to force)))
        true
    }

    /** [regionId] is a branch region ("b|<brandKey>|<kakaoId>"). Alerts with the brand's active coupons. */
    suspend fun notifyBranch(regionId: String, force: Boolean = false, now: Long = System.currentTimeMillis()): Boolean = lock.withLock {
        val key = regionId.split("|").getOrNull(1).orEmpty()
        val all = coupons.getAll()
        when (val decision = decideBranchAlert(all, key, force, now)) {
            is BranchDecision.Suppress -> {
                events.append(LogEvent(now, EventKind.NOTIFY_SUPPRESSED, regionId, mapOf("reason" to decision.reason.label)))
                false
            }
            is BranchDecision.Show -> {
                val branch = windows.load()?.branches?.get(regionId)
                val usable = decision.coupons
                if (!deliver(couponAlert(branch?.name ?: usable.first().brand, usable), regionId, now)) return@withLock false
                coupons.markNotified(usable.map { it.id }, now)
                events.append(
                    LogEvent(now, EventKind.NOTIFY_SHOWN, regionId, mapOf("type" to "branch", "branch" to branch?.name, "forced" to force)),
                )
                true
            }
        }
    }

    /** Shows the alert, or records that it could not be shown. A failed alert does not start a cooldown. */
    private suspend fun deliver(alert: Alert, logId: String, now: Long): Boolean {
        if (sink.show(alert)) return true
        events.append(LogEvent(now, EventKind.NOTIFY_SUPPRESSED, logId, mapOf("reason" to SuppressReason.NO_PERMISSION.label)))
        return false
    }
}
