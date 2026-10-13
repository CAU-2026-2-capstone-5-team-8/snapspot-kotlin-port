package com.capstone.snapspot.data.permissions

import android.app.PendingIntent
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The phone's own location state. [on] is the location switch: without it nothing works, permissions or not.
 * [accuracyFix] is a pop-up from Google Play services that turns on Google Location Accuracy; it is only set
 * when that would help, and it is a recommendation, not a requirement.
 */
data class DeviceLocation(val on: Boolean, val accuracyFix: PendingIntent? = null) {
    val accuracyLow: Boolean get() = accuracyFix != null
}

@Singleton
class LocationSettingsChecker @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun check(): DeviceLocation {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) return DeviceLocation(on = false)

        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build())
            .build()
        return try {
            LocationServices.getSettingsClient(context).checkLocationSettings(request).await()
            DeviceLocation(on = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResolvableApiException) {
            DeviceLocation(on = true, accuracyFix = e.resolution)
        } catch (_: Exception) {
            // Play services missing or failing: say nothing about accuracy rather than nag about a guess.
            DeviceLocation(on = true)
        }
    }
}
