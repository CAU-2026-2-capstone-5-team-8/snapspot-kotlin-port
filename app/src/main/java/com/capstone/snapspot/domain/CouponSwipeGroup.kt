package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coupon
import java.time.ZoneId

/**
 * The coupons the coupon screen lets the user swipe through when [current] is open: the active coupons of the same
 * brand, soonest-expiring first. A coupon that is used or expired is shown alone, and so is a brand with one coupon.
 */
fun swipeGroup(
    coupons: List<Coupon>,
    current: Coupon,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Coupon> {
    if (!isActive(current, now, zone)) return listOf(current)
    val group = couponsForBrand(coupons, brandKey(current.brand), now, zone)
    // [current] is part of its own group even if the list it came from is a moment out of date.
    return if (group.any { it.id == current.id }) group else listOf(current)
}
