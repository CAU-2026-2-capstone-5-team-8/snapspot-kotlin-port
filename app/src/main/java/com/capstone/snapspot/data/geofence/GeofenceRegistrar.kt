package com.capstone.snapspot.data.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.capstone.snapspot.data.permissions.PermissionReader
import com.capstone.snapspot.domain.RegionRegistrar
import com.capstone.snapspot.domain.model.Region
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the planned regions with Google Play services. Everything registered earlier is dropped first, so
 * a region that left the plan stops alerting. Without "always" location access nothing is registered: the OS
 * would not deliver the events with the app closed anyway.
 */
@Singleton
class GeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionReader,
) : RegionRegistrar {
    private val client = LocationServices.getGeofencingClient(context)

    // Delivered as a broadcast; the system fills in the event, so the intent must be mutable.
    private val pending: PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission") // checked through PermissionReader
    override suspend fun register(regions: List<Region>) {
        if (regions.isEmpty()) return clear()
        if (!permissions.read().bg) return
        val request = GeofencingRequest.Builder()
            // Standing inside a branch when the plan is registered counts as arriving there.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(regions.map(::toGeofence))
            .build()
        client.removeGeofences(pending).await()
        client.addGeofences(request, pending).await()
    }

    override suspend fun clear() {
        client.removeGeofences(pending).await()
    }

    private fun toGeofence(r: Region): Geofence = Geofence.Builder()
        .setRequestId(r.identifier)
        .setCircularRegion(r.latitude, r.longitude, r.radius.toFloat())
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(
            (if (r.notifyOnEnter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                (if (r.notifyOnExit) Geofence.GEOFENCE_TRANSITION_EXIT else 0),
        )
        .build()
}
