package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coupon
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

/** Passing several branches of one brand in a row should alert once, not at every door. */
const val BRAND_COOLDOWN_MS = 3L * 60 * 60 * 1000

private const val DAY_MS = 86_400_000L

/**
 * End of the given YYYY-MM-DD day (23:59:59, local time) in epoch millis, or null when [date] is
 * not a real date. Shared with the notify policy so both read dates the same way.
 */
fun endOfDayMillis(date: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
    try {
        LocalDate.parse(date).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }

/** A coupon with no expiry, or an expiry we cannot read, stays active; used coupons never are. */
fun isActive(c: Coupon, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
    if (c.usedAt != null) return false
    val expires = c.expiresAt ?: return true
    val end = endOfDayMillis(expires, zone) ?: return true
    return end >= now
}

/** Whole days until expiry (0 = expires today), or null when there is no readable expiry date. */
fun daysLeft(c: Coupon, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
    val expires = c.expiresAt ?: return null
    val end = endOfDayMillis(expires, zone) ?: return null
    return Math.floorDiv(end - now, DAY_MS)
}

/** Active coupons of one brand, soonest-expiring first. */
fun couponsForBrand(
    coupons: List<Coupon>,
    key: String,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Coupon> =
    coupons
        .filter { brandKey(it.brand) == key && isActive(it, now, zone) }
        .sortedBy { it.expiresAt ?: "9999" }

/** True when this brand alerted recently (through any of its coupons). */
fun brandCoolingDown(coupons: List<Coupon>, key: String, now: Long = System.currentTimeMillis()): Boolean =
    coupons.any { c ->
        brandKey(c.brand) == key && c.lastNotifiedAt != null && now - c.lastNotifiedAt < BRAND_COOLDOWN_MS
    }
