package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.PlaceQuery
import kotlin.coroutines.cancellation.CancellationException

/** Below this, the best Kakao hit is offered as a choice but the device geocoder places the pin. */
const val MIN_AUTO_MATCH = 0.4

private const val MAX_CANDIDATES = 4

/** The device's own geocoder: coordinates for a name or address, or null. */
interface GeocodePort {
    suspend fun geocode(name: String, address: String): Coords?
}

data class Located(val coords: Coords?, val matched: Candidate?, val candidates: List<Candidate>)

/**
 * Kakao Local first (it knows shop names), the device geocoder as fallback.
 * @param api null when there is no Kakao key; the search is skipped then.
 */
suspend fun locatePlace(name: String, address: String, category: String, api: KakaoLocalApi?, geocoder: GeocodePort): Located {
    var candidates = emptyList<Candidate>()
    if (api != null && (name.isNotEmpty() || address.isNotEmpty())) {
        candidates = try {
            searchPlace(PlaceQuery(name, address, category), api).take(MAX_CANDIDATES)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList() // a failed search falls back to the geocoder instead of blocking the add flow
        }
    }
    val best = candidates.firstOrNull()
    if (best != null && best.score >= MIN_AUTO_MATCH) {
        return Located(Coords(best.latitude, best.longitude), best, candidates)
    }
    return Located(geocoder.geocode(name, address), null, candidates)
}
