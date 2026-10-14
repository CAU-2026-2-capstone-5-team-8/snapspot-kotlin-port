package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Place
import java.time.ZoneId

private const val DAY_MS = 86_400_000L

/** What the short description of a place on the map says. A line that has nothing to say is null. */
data class PlaceInfo(
    val name: String,
    val category: String,
    /** "10월 31일까지". */
    val endLabel: String?,
    /** Whole days until the end date (0 = ends today); null without a readable end date, or once it has passed. */
    val daysLeft: Long?,
    val note: String?,
)

fun placeInfo(place: Place, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): PlaceInfo {
    val end = place.endDate?.let { endOfDayMillis(it, zone) }
    val days = end?.let { Math.floorDiv(it - now, DAY_MS) }?.takeIf { it >= 0 }
    return PlaceInfo(
        name = place.name,
        category = place.category,
        endLabel = endLabel(place.endDate),
        daysLeft = days,
        note = place.note.trim().ifEmpty { null },
    )
}
