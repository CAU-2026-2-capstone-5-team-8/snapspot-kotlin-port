package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotAnalyzerTest {
    @Test
    fun `an empty result becomes a blank place with category 기타`() {
        val e = sanitize(RawExtraction())
        assertEquals(Kind.PLACE, e.kind)
        assertEquals("기타", e.category)
        assertEquals("", e.name)
        assertFalse(e.isPlace)
        assertNull(e.endDate)
    }

    @Test
    fun `text is trimmed and only the word coupon makes a coupon`() {
        val e = sanitize(
            RawExtraction(kind = "coupon", brand = " 스타벅스 ", benefit = " 아메리카노 Tall\n", isPlace = false, category = " 카페 "),
        )
        assertEquals(Kind.COUPON, e.kind)
        assertEquals("스타벅스", e.brand)
        assertEquals("아메리카노 Tall", e.benefit)
        assertEquals("카페", e.category)
        assertEquals(Kind.PLACE, sanitize(RawExtraction(kind = "Coupon")).kind)
        assertEquals(Kind.PLACE, sanitize(RawExtraction(kind = "place")).kind)
    }

    @Test
    fun `a blank category falls back to 기타`() {
        assertEquals("기타", sanitize(RawExtraction(category = "   ")).category)
    }

    @Test
    fun `isPlace is true only when the analyzer said so`() {
        assertTrue(sanitize(RawExtraction(isPlace = true)).isPlace)
        assertFalse(sanitize(RawExtraction(isPlace = null)).isPlace)
    }

    @Test
    fun `endDate must look like YYYY-MM-DD`() {
        assertEquals("2026-10-31", sanitize(RawExtraction(endDate = "2026-10-31")).endDate)
        assertNull(sanitize(RawExtraction(endDate = "2026.10.31")).endDate)
        assertNull(sanitize(RawExtraction(endDate = "10월 31일")).endDate)
        assertNull(sanitize(RawExtraction(endDate = "2026-10-31 ")).endDate)
        assertNull(sanitize(RawExtraction(endDate = "")).endDate)
    }

    @Test
    fun `a date with the right shape but not real is kept, like the original`() {
        assertEquals("2026-13-45", sanitize(RawExtraction(endDate = "2026-13-45")).endDate)
    }
}
