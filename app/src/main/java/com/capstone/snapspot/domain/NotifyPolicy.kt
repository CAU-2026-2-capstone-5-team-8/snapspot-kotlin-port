package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import java.time.ZoneId

/** Do not alert about the same place more than once in this window. */
const val COOLDOWN_MS = 6L * 60 * 60 * 1000

/**
 * Why an alert was held back. [label] is the Korean text written to the event log; it matches the
 * React Native app so exported logs from both read the same.
 */
enum class SuppressReason(val label: String) {
    ENDED("운영 종료"),
    RECENT("최근에 알림"),
    NO_COUPON("사용할 쿠폰 없음"),
    BRAND_COOLDOWN("브랜드 쿨다운"),

    /** The alert could not be shown (notifications are off). Not in the React Native app, which failed silently. */
    NO_PERMISSION("알림 권한 없음"),
}

/** Why a place should NOT alert right now, or null if it should. */
fun suppressReason(
    place: Place,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): SuppressReason? {
    place.endDate?.let { date ->
        val end = endOfDayMillis(date, zone)
        if (end != null && end < now) return SuppressReason.ENDED
    }
    val last = place.lastNotifiedAt
    if (last != null && now - last < COOLDOWN_MS) return SuppressReason.RECENT
    return null
}

sealed interface BranchDecision {
    /** Alert with these coupons, soonest-expiring first. */
    data class Show(val coupons: List<Coupon>) : BranchDecision

    data class Suppress(val reason: SuppressReason) : BranchDecision
}

/**
 * A branch geofence of brand [key] was entered. Alerts with the brand's active coupons, once per
 * brand per cooldown. [force] skips the cooldown (the "알림 테스트" button), not the coupon check.
 */
fun decideBranchAlert(
    coupons: List<Coupon>,
    key: String,
    force: Boolean = false,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): BranchDecision {
    val usable = couponsForBrand(coupons, key, now, zone)
    if (usable.isEmpty()) return BranchDecision.Suppress(SuppressReason.NO_COUPON)
    if (!force && brandCoolingDown(coupons, key, now)) return BranchDecision.Suppress(SuppressReason.BRAND_COOLDOWN)
    return BranchDecision.Show(usable)
}
