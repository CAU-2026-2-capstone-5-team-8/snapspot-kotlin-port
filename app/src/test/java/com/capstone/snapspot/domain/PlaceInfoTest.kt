package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceInfoTest {
    private val now = seoulMillis(2026, 10, 14, 12)

    private fun place(endDate: String? = null, note: String = "소금빵 맛집", category: String = "카페", name: String = "어니언 성수") = Place(
        id = "p", name = name, address = "서울 성동구 아차산로9길 8", category = category, note = note, endDate = endDate,
        latitude = 37.5447, longitude = 127.0582, radius = 200, imageUri = null, createdAt = 0, lastNotifiedAt = null,
    )

    private fun info(p: Place) = placeInfo(p, now, SEOUL)

    @Test
    fun `name category and note are passed on`() {
        val i = info(place())
        assertEquals("어니언 성수", i.name)
        assertEquals("카페", i.category)
        assertEquals("소금빵 맛집", i.note)
    }

    @Test
    fun `an end date gives the label and the days left`() {
        val i = info(place(endDate = "2026-10-31"))
        assertEquals("10월 31일까지", i.endLabel)
        assertEquals(17L, i.daysLeft)
    }

    @Test
    fun `a place that ends today has zero days left`() {
        assertEquals(0L, info(place(endDate = "2026-10-14")).daysLeft)
    }

    @Test
    fun `a place that has ended has no days left but keeps its label`() {
        val i = info(place(endDate = "2026-10-01"))
        assertNull(i.daysLeft)
        assertEquals("10월 1일까지", i.endLabel)
    }

    @Test
    fun `no end date gives neither label nor days`() {
        val i = info(place(endDate = null))
        assertNull(i.endLabel)
        assertNull(i.daysLeft)
    }

    @Test
    fun `an end date that is not a date gives no days`() {
        assertNull(info(place(endDate = "곧")).daysLeft)
    }

    @Test
    fun `a blank note is left out`() {
        assertNull(info(place(note = "")).note)
        assertNull(info(place(note = "   ")).note)
    }

    @Test
    fun `a note is trimmed`() {
        assertEquals("웨이팅 없음", info(place(note = "  웨이팅 없음 \n")).note)
    }

    @Test
    fun `days left count the same way as the coupon screen`() {
        // Ends on the 15th: tomorrow, so 1 day.
        assertEquals(1L, info(place(endDate = "2026-10-15")).daysLeft)
    }
}
