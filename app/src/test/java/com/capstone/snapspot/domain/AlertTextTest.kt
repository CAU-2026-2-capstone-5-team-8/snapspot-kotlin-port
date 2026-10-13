package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTextTest {
    private fun place(
        id: String = "p1",
        note: String = "",
        lat: Double = 37.5450,
        radius: Int = 200,
        endDate: String? = null,
        last: Long? = null,
    ) = Place(id, "어니언 성수", "서울 성동구", "카페", note, endDate, lat, 127.0570, radius, null, null, null, 0, last)

    @Test
    fun `a place alert names the category and the place`() {
        val a = placeAlert(place())
        assertEquals(AlertKind.PLACE, a.kind)
        assertEquals("p1", a.targetId)
        assertEquals("📍 저장해 둔 카페 근처예요", a.title)
        assertEquals("어니언 성수", a.body)
    }

    @Test
    fun `a place note is added after a dot`() {
        assertEquals("어니언 성수 · 앙버터 먹기", placeAlert(place(note = "앙버터 먹기")).body)
    }

    @Test
    fun `a coupon alert names the branch, the coupon and when it ends`() {
        val a = couponAlert("스타벅스 성수점", listOf(coupon(id = "c1", expiresAt = "2026-12-31")))
        assertEquals(AlertKind.COUPON, a.kind)
        assertEquals("c1", a.targetId)
        assertEquals("🎟️ 스타벅스 성수점 근처예요", a.title)
        assertEquals("스타벅스 아메리카노 · 12월 31일까지", a.body)
    }

    @Test
    fun `a coupon without an end date has no date part`() {
        assertEquals("스타벅스 아메리카노", couponAlert("스타벅스 성수점", listOf(coupon())).body)
    }

    @Test
    fun `several coupons show the first one and count the rest`() {
        val a = couponAlert("스타벅스 성수점", listOf(coupon(id = "c1", expiresAt = "2026-11-02"), coupon(id = "c2"), coupon(id = "c3")))
        assertEquals("스타벅스 아메리카노 · 11월 2일까지 외 2장", a.body)
        assertEquals("c1", a.targetId) // tapping opens the first
    }

    // --- placesToAlert ---

    private val here = Coords(37.5450, 127.0570)
    private val now = seoulMillis(2026, 10, 10, 12)

    @Test
    fun `only places the user is inside are picked`() {
        val inside = place("in", lat = 37.5452, radius = 200)
        val outside = place("out", lat = 37.5600, radius = 200)
        assertEquals(listOf("in"), placesToAlert(listOf(inside, outside), here, now, SEOUL).map { it.id })
    }

    @Test
    fun `the radius is each place's own`() {
        val wide = place("wide", lat = 37.5470, radius = 500) // ~220 m away
        val narrow = place("narrow", lat = 37.5470, radius = 100)
        assertEquals(listOf("wide"), placesToAlert(listOf(wide, narrow), here, now, SEOUL).map { it.id })
    }

    @Test
    fun `places held back are left out so the log is not filled while standing still`() {
        val ended = place("ended", endDate = "2026-10-01")
        val recent = place("recent", last = now - 60_000)
        val fresh = place("fresh")
        assertEquals(listOf("fresh"), placesToAlert(listOf(ended, recent, fresh), here, now, SEOUL).map { it.id })
    }

    @Test
    fun `nothing is picked when no place is near`() {
        assertTrue(placesToAlert(emptyList(), here, now, SEOUL).isEmpty())
    }
}
