package com.capstone.snapspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponRulesTest {
    private val now = seoulMillis(2026, 10, 10, 12)

    @Test
    fun `a coupon stays active through its last day, then expires`() {
        assertTrue(isActive(coupon(expiresAt = "2026-10-10"), now, SEOUL))
        assertFalse(isActive(coupon(expiresAt = "2026-10-09"), now, SEOUL))
        assertFalse(isActive(coupon(usedAt = 1), now, SEOUL))
        assertTrue(isActive(coupon(), now, SEOUL))
    }

    @Test
    fun `daysLeft counts whole days, 0 on the last day`() {
        assertEquals(0L, daysLeft(coupon(expiresAt = "2026-10-10"), now, SEOUL))
        assertEquals(21L, daysLeft(coupon(expiresAt = "2026-10-31"), now, SEOUL))
        assertNull(daysLeft(coupon(), now, SEOUL))
    }

    @Test
    fun `daysLeft floors toward the past like the original`() {
        assertEquals(-1L, daysLeft(coupon(expiresAt = "2026-10-09"), now, SEOUL))
        assertEquals(-2L, daysLeft(coupon(expiresAt = "2026-10-08"), now, SEOUL))
    }

    @Test
    fun `an unreadable expiry date means no expiry`() {
        val bad = coupon(expiresAt = "2026-13-45")
        assertTrue(isActive(bad, now, SEOUL))
        assertNull(daysLeft(bad, now, SEOUL))
    }

    @Test
    fun `couponsForBrand matches across spacing and puts the soonest expiry first`() {
        val list = listOf(
            coupon(id = "late", brand = "스타 벅스", expiresAt = "2026-12-01"),
            coupon(id = "soon", expiresAt = "2026-10-20"),
            coupon(id = "other", brand = "올리브영"),
            coupon(id = "used", usedAt = 1),
        )
        assertEquals(listOf("soon", "late"), couponsForBrand(list, "스타벅스", now, SEOUL).map { it.id })
    }

    @Test
    fun `coupons without an expiry sort last`() {
        val list = listOf(coupon(id = "none"), coupon(id = "dated", expiresAt = "2026-11-01"))
        assertEquals(listOf("dated", "none"), couponsForBrand(list, "스타벅스", now, SEOUL).map { it.id })
    }

    @Test
    fun `one alert for a brand cools down all its coupons`() {
        val list = listOf(coupon(id = "a", lastNotifiedAt = now - 1000), coupon(id = "b"))
        assertTrue(brandCoolingDown(list, "스타벅스", now))
        assertFalse(brandCoolingDown(list, "스타벅스", now + BRAND_COOLDOWN_MS))
        assertFalse(brandCoolingDown(list, "올리브영", now))
    }
}
