package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Haversine distance in meters. */
fun distanceMeters(a: Coords, b: Coords): Double {
    fun rad(d: Double) = d * Math.PI / 180
    val dLat = rad(b.latitude - a.latitude)
    val dLng = rad(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.latitude)) * cos(rad(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToLong()}m" else String.format(java.util.Locale.ROOT, "%.1fkm", m / 1000)
