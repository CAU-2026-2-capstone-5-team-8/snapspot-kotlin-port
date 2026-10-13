package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Region

/** Shows an alert to the user. False when it could not be shown (for example, notifications are off). */
interface AlertSink {
    fun show(alert: Alert): Boolean
}

/** Hands the planned regions to the operating system so it wakes the app when one is entered or left. */
interface RegionRegistrar {
    /** Replaces everything registered before. */
    suspend fun register(regions: List<Region>)

    suspend fun clear()
}
