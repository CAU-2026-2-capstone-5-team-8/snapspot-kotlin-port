package com.capstone.snapspot.data.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.capstone.snapspot.domain.GeocodePort
import com.capstone.snapspot.domain.model.Coords
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/** The device's own geocoder: turns an extracted name or address into coordinates. Most specific query first. */
class AndroidGeocoder @Inject constructor(@ApplicationContext private val context: Context) : GeocodePort {

    override suspend fun geocode(name: String, address: String): Coords? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.KOREA)
        val queries = listOf(listOf(address, name).filter { it.isNotEmpty() }.joinToString(" "), address, name)
            .filter { it.isNotEmpty() }
            .distinct()
        for (q in queries) {
            try {
                val first = lookup(geocoder, q).firstOrNull() ?: continue
                return Coords(first.latitude, first.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // try the next query
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun lookup(geocoder: Geocoder, query: String): List<Address> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(
                    query,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(emptyList())
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, 1).orEmpty() }
        }
}
