package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import java.time.ZoneId

enum class AlertKind { PLACE, COUPON }

/** What a notification says and what tapping it opens. [targetId] is a place id or a coupon id. */
data class Alert(val kind: AlertKind, val targetId: String, val title: String, val body: String)

/** The alert for a saved place the user is near. Same wording as the React Native app. */
fun placeAlert(place: Place): Alert {
    val extra = if (place.note.isNotEmpty()) " · ${place.note}" else ""
    return Alert(AlertKind.PLACE, place.id, "📍 저장해 둔 ${place.category} 근처예요", "${place.name}$extra")
}

/** The alert for a branch the user is near: the soonest-expiring coupon, and how many more there are. */
fun couponAlert(branchName: String, coupons: List<Coupon>): Alert {
    val first = coupons.first()
    val more = if (coupons.size > 1) " 외 ${coupons.size - 1}장" else ""
    val until = endLabel(first.expiresAt)?.let { " · $it" }.orEmpty()
    return Alert(AlertKind.COUPON, first.id, "🎟️ $branchName 근처예요", "${first.brand} ${first.benefit}$until$more")
}

/**
 * Saved places the user is standing in that would alert right now. Places that are held back (ended,
 * alerted recently) are left out so a position update every few meters does not fill the log.
 */
fun placesToAlert(
    places: List<Place>,
    here: Coords,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Place> = places.filter {
    distanceMeters(here, Coords(it.latitude, it.longitude)) <= it.radius && suppressReason(it, now, zone) == null
}
