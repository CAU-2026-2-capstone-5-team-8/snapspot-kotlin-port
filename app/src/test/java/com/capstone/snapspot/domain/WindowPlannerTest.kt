package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowPlannerTest {
    private val here = Coords(37.5446, 127.0565)

    /** n spots going east from `here`, 100m apart. */
    private fun line(group: String, n: Int, startM: Int = 100): List<Spot> =
        List(n) { i ->
            Spot(
                id = "$group-$i",
                group = group,
                latitude = here.latitude,
                longitude = here.longitude + (startM + i * 100) / 88000.0, // ~88km per degree of longitude at 37.5N
                radius = 200,
            )
        }

    @Test
    fun `reserves one exit-only refresh region centered on the user`() {
        val regions = planRegions(here, line("A", 5), slots = 10, refreshRadius = 1000).regions
        val refresh = regions.find { it.identifier == REFRESH_ID }
        assertNotNull("refresh region missing", refresh)
        assertTrue(refresh!!.notifyOnExit)
        assertEquals(false, refresh.notifyOnEnter)
        assertEquals(1000, refresh.radius)
        assertEquals(here.latitude, refresh.latitude, 0.0)
    }

    @Test
    fun `never exceeds the slot limit, refresh region included`() {
        val regions = planRegions(here, line("A", 50) + line("B", 50), slots = 20, refreshRadius = 1000).regions
        assertEquals(20, regions.size)
    }

    @Test
    fun `shares slots fairly so one brand with many branches cannot starve the others`() {
        val spots = line("place", 3, 5000) + line("A", 50) + line("B", 50, 300)
        val byGroup = planRegions(here, spots, slots = 11, refreshRadius = 1000).byGroup
        // 10 usable slots, round-robin over 3 groups: place 3 (all of them), A 4, B 3.
        assertEquals(mapOf("place" to 3, "A" to 4, "B" to 3), byGroup)
    }

    @Test
    fun `picks the nearest spots within each group`() {
        val spots = line("A", 10).reversed() // input order must not matter
        val regions = planRegions(here, spots, slots = 4, refreshRadius = 1000).regions
        assertEquals(listOf("A-0", "A-1", "A-2"), regions.filter { it.identifier != REFRESH_ID }.map { it.identifier })
    }

    @Test
    fun `without a known position there is no refresh region and input order is kept`() {
        val regions = planRegions(null, line("place", 3), slots = 10, refreshRadius = 1000).regions
        assertEquals(listOf("place-0", "place-1", "place-2"), regions.map { it.identifier })
    }
}
