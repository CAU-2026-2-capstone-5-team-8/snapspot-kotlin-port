package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun `distance between a point and itself is zero`() {
        val p = Coords(37.5446, 127.0565)
        assertEquals(0.0, distanceMeters(p, p), 0.0)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val d = distanceMeters(Coords(37.0, 127.0), Coords(38.0, 127.0))
        assertEquals(111_195.0, d, 5.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = Coords(37.5446, 127.0565)
        val b = Coords(37.5665, 126.9780)
        assertEquals(distanceMeters(a, b), distanceMeters(b, a), 1e-6)
    }

    @Test
    fun `formatDistance uses meters below a kilometer and km above`() {
        assertEquals("0m", formatDistance(0.0))
        assertEquals("999m", formatDistance(999.4))
        assertEquals("1.0km", formatDistance(1000.0))
        assertEquals("12.3km", formatDistance(12_340.0))
    }
}
