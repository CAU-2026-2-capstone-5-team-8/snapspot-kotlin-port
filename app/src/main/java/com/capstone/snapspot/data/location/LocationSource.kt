package com.capstone.snapspot.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.capstone.snapspot.domain.draft.HerePort
import com.capstone.snapspot.domain.model.Coords
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The user's position while the app is open. [current] answers from a recent fix when there is one,
 * otherwise asks the device. Without the location permission it gives null.
 */
@Singleton
class LocationSource @Inject constructor(@ApplicationContext private val context: Context) : HerePort {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _here = MutableStateFlow<Coords?>(null)

    /** The latest known position, for screens that show distances. */
    val here: StateFlow<Coords?> = _here.asStateFlow()

    private var fixedAt = 0L

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    override suspend fun current(): Coords? {
        if (!hasPermission()) return null
        val known = _here.value
        if (known != null && System.currentTimeMillis() - fixedAt < FRESH_MS) return known
        return refresh()
    }

    /** Asks the device for a new fix; falls back to its last known one. */
    @android.annotation.SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun refresh(): Coords? {
        if (!hasPermission()) return null
        return try {
            // One-shot, and the pin should sit where the user is standing, so ask for the best fix.
            val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
            val loc = fresh ?: client.lastLocation.await()
            loc?.let {
                Coords(it.latitude, it.longitude).also { c ->
                    _here.value = c
                    fixedAt = System.currentTimeMillis()
                }
            } ?: _here.value
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _here.value
        }
    }

    /**
     * A position good enough to plan around, for work that runs in the background: the device's last fix when it
     * is under a minute old, otherwise a new balanced-power one. Null when neither is available.
     */
    @android.annotation.SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun quickFix(): Coords? {
        if (!hasPermission()) return null
        return try {
            val last = client.lastLocation.await()
            val ageMs = last?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
            val loc = if (last != null && ageMs != null && ageMs < FRESH_MS) {
                last
            } else {
                withTimeoutOrNull(FIX_TIMEOUT_MS) { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() } ?: last
            }
            loc?.let { Coords(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Positions while collected, one each time the user has moved [minDistanceM] meters. For the app being open:
     * it keeps distances live and catches arrivals without waiting for the OS geofence. Collect only while the
     * app is visible.
     */
    @android.annotation.SuppressLint("MissingPermission") // checked by hasPermission()
    fun watch(minDistanceM: Float = WATCH_DISTANCE_M): Flow<Coords> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, WATCH_INTERVAL_MS)
            .setMinUpdateDistanceMeters(minDistanceM)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(Coords(it.latitude, it.longitude)) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** Records a position found elsewhere (the watcher), so every screen's distances follow it. */
    fun update(at: Coords) {
        _here.value = at
        fixedAt = System.currentTimeMillis()
    }

    private companion object {
        const val FRESH_MS = 60_000L
        const val FIX_TIMEOUT_MS = 10_000L
        const val WATCH_DISTANCE_M = 25f
        const val WATCH_INTERVAL_MS = 10_000L
    }
}
