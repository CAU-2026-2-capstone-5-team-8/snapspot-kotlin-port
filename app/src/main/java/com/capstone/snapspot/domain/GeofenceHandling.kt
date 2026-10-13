package com.capstone.snapspot.domain

enum class Transition { ENTER, EXIT }

/** What a geofence event can set in motion. The data layer supplies the real ones. */
interface GeofenceActions {
    /** Plan again around where the user is now. May take a while, so it should not block the event. */
    fun replan()

    suspend fun notifyPlace(placeId: String)

    suspend fun notifyBranch(regionId: String)
}

/**
 * One geofence event. It is logged first, even when nothing follows, so the log shows every event the
 * OS delivered. Leaving the refresh circle re-plans; entering a branch or a place alerts.
 */
suspend fun handleGeofence(
    type: Transition,
    regionId: String,
    now: Long,
    log: suspend (LogEvent) -> Unit,
    actions: GeofenceActions,
) {
    log(LogEvent(now, if (type == Transition.ENTER) EventKind.GEOFENCE_ENTER else EventKind.GEOFENCE_EXIT, regionId))
    when {
        regionId == REFRESH_ID -> if (type == Transition.EXIT) actions.replan()
        type != Transition.ENTER -> Unit
        isBranchRegion(regionId) -> actions.notifyBranch(regionId)
        else -> actions.notifyPlace(regionId)
    }
}
