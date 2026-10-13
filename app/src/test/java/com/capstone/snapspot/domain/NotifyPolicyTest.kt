package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyPolicyTest {
    private val now = seoulMillis(2026, 10, 10, 12)

    private fun place(endDate: String? = null, lastNotifiedAt: Long? = null) = Place(
        id = "p", name = "어니언 성수", address = "", category = "카페", note = "", endDate = endDate,
        latitude = 37.5, longitude = 127.0, radius = 200, imageUri = null, createdAt = 0, lastNotifiedAt = lastNotifiedAt,
    )

    @Test
    fun `a fresh place alerts`() {
        assertNull(suppressReason(place(), now, SEOUL))
        assertNull(suppressReason(place(endDate = "2026-10-10"), now, SEOUL)) // still open through its last day
    }

    @Test
    fun `an ended place does not alert`() {
        assertEquals(SuppressReason.ENDED, suppressReason(place(endDate = "2026-10-09"), now, SEOUL))
    }

    @Test
    fun `an unreadable end date does not silence a place`() {
        assertNull(suppressReason(place(endDate = "2026-13-45"), now, SEOUL))
    }

    @Test
    fun `a place cools down for six hours`() {
        assertEquals(SuppressReason.RECENT, suppressReason(place(lastNotifiedAt = now - 1000), now, SEOUL))
        assertNull(suppressReason(place(lastNotifiedAt = now - COOLDOWN_MS), now, SEOUL))
    }

    @Test
    fun `reason labels match the React Native event log`() {
        assertEquals("운영 종료", SuppressReason.ENDED.label)
        assertEquals("최근에 알림", SuppressReason.RECENT.label)
        assertEquals("사용할 쿠폰 없음", SuppressReason.NO_COUPON.label)
        assertEquals("브랜드 쿨다운", SuppressReason.BRAND_COOLDOWN.label)
    }

    @Test
    fun `a branch alert needs an active coupon for the brand`() {
        val d = decideBranchAlert(listOf(coupon(brand = "올리브영")), "스타벅스", now = now, zone = SEOUL)
        assertEquals(BranchDecision.Suppress(SuppressReason.NO_COUPON), d)
        val expired = decideBranchAlert(listOf(coupon(expiresAt = "2026-10-01")), "스타벅스", now = now, zone = SEOUL)
        assertEquals(BranchDecision.Suppress(SuppressReason.NO_COUPON), expired)
    }

    @Test
    fun `a branch alert lists the brand's coupons soonest first`() {
        val list = listOf(coupon(id = "late", expiresAt = "2026-12-01"), coupon(id = "soon", expiresAt = "2026-10-20"))
        val d = decideBranchAlert(list, "스타벅스", now = now, zone = SEOUL) as BranchDecision.Show
        assertEquals(listOf("soon", "late"), d.coupons.map { it.id })
    }

    @Test
    fun `a brand cools down after one alert, unless forced`() {
        val list = listOf(coupon(id = "a", lastNotifiedAt = now - 1000), coupon(id = "b"))
        assertEquals(
            BranchDecision.Suppress(SuppressReason.BRAND_COOLDOWN),
            decideBranchAlert(list, "스타벅스", now = now, zone = SEOUL),
        )
        assertTrue(decideBranchAlert(list, "스타벅스", force = true, now = now, zone = SEOUL) is BranchDecision.Show)
    }

    @Test
    fun `force does not bypass the missing-coupon check`() {
        val d = decideBranchAlert(emptyList(), "스타벅스", force = true, now = now, zone = SEOUL)
        assertEquals(BranchDecision.Suppress(SuppressReason.NO_COUPON), d)
    }
}
