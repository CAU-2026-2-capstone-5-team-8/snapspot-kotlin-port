package com.capstone.snapspot.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.capstone.snapspot.data.notify.AlertCenter
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.domain.GeofenceActions
import com.capstone.snapspot.domain.Transition
import com.capstone.snapspot.domain.handleGeofence
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** What the OS-delivered events set in motion: alerts now, re-planning through [enqueueReplan]. */
@Singleton
class GeofenceDispatcher @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val alerts: AlertCenter,
) : GeofenceActions {
    override fun replan() = enqueueReplan(context, fallbackToSaved = false)

    override suspend fun notifyPlace(placeId: String) {
        alerts.notifyPlace(placeId)
    }

    override suspend fun notifyBranch(regionId: String) {
        alerts.notifyBranch(regionId)
    }
}

/** Woken by the OS when a registered region is entered or left, also with the app closed. */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: GeofenceDispatcher

    @Inject lateinit var events: EventLogRepository

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val type = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> Transition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> Transition.EXIT
            else -> return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }
        if (ids.isEmpty()) return

        // Database work does not belong on the main thread, and a receiver may only hold on for a few seconds.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(BUDGET_MS) {
                    for (id in ids) handleGeofence(type, id, System.currentTimeMillis(), events::append, actions)
                }
            } catch (_: Exception) {
                // Out of time or a failed alert: the log has what was recorded, and the next event tries again.
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BUDGET_MS = 9_000L
    }
}
