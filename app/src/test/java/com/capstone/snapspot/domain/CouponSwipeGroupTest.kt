package com.capstone.snapspot.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CouponSwipeGroupTest {
    private val now = seoulMillis(2026, 10, 14, 12)

    private fun group(all: List<com.capstone.snapspot.domain.model.Coupon>, currentId: String) =
        swipeGroup(all, all.first { it.id == currentId }, now, SEOUL).map { it.id }

    @Test
    fun `the active coupons of the brand come soonest-expiring first`() {
        val all = listOf(
            coupon("late", "메가MGC커피", expiresAt = "2026-11-30"),
            coupon("soon", "메가MGC커피", expiresAt = "2026-10-25"),
        )
        assertEquals(listOf("soon", "late"), group(all, "late"))
        assertEquals(listOf("soon", "late"), group(all, "soon"))
    }

    @Test
    fun `other brands are not in the group`() {
        val all = listOf(
            coupon("a", "메가MGC커피", expiresAt = "2026-11-30"),
            coupon("b", "스타벅스", expiresAt = "2026-10-25"),
            coupon("c", "메가MGC커피", expiresAt = "2026-12-01"),
        )
        assertEquals(listOf("a", "c"), group(all, "a"))
        assertEquals(listOf("b"), group(all, "b"))
    }

    @Test
    fun `used and expired coupons are left out`() {
        val all = listOf(
            coupon("ok", "올리브영", expiresAt = "2026-11-20"),
            coupon("used", "올리브영", expiresAt = "2026-11-21", usedAt = 1L),
            coupon("gone", "올리브영", expiresAt = "2026-10-01"),
            coupon("ok2", "올리브영", expiresAt = "2026-11-22"),
        )
        assertEquals(listOf("ok", "ok2"), group(all, "ok"))
    }

    @Test
    fun `a used or expired coupon is shown alone`() {
        val all = listOf(
            coupon("ok", "올리브영", expiresAt = "2026-11-20"),
            coupon("used", "올리브영", expiresAt = "2026-11-21", usedAt = 1L),
            coupon("gone", "올리브영", expiresAt = "2026-10-01"),
        )
        assertEquals(listOf("used"), group(all, "used"))
        assertEquals(listOf("gone"), group(all, "gone"))
    }

    @Test
    fun `a coupon without an expiry date comes last`() {
        val all = listOf(
            coupon("none", "파리바게뜨", expiresAt = null),
            coupon("dated", "파리바게뜨", expiresAt = "2026-12-31"),
        )
        assertEquals(listOf("dated", "none"), group(all, "none"))
    }

    @Test
    fun `one coupon of a brand is a group of one`() {
        val all = listOf(coupon("only", "스타벅스", expiresAt = "2026-11-30"))
        assertEquals(listOf("only"), group(all, "only"))
    }

    @Test
    fun `the brand is matched the way the rest of the app matches it`() {
        val all = listOf(
            coupon("a", "메가MGC커피", expiresAt = "2026-11-30"),
            coupon("b", "메가mgc 커피", expiresAt = "2026-12-01"),
        )
        assertEquals(listOf("a", "b"), group(all, "a"))
    }

    @Test
    fun `a current coupon missing from the list still shows itself`() {
        val stale = coupon("x", "스타벅스", expiresAt = "2026-11-30")
        assertEquals(listOf("x"), swipeGroup(emptyList(), stale, now, SEOUL).map { it.id })
    }
}
