package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapBranchLimitTest {
    private val center = Coords(37.5446, 127.0565)

    /** A branch [meters] due north of [center]. */
    private fun at(meters: Int, brand: String = "스타벅스", name: String = "$brand ${meters}m") =
        BranchRef(name, brandKey(brand), center.latitude + meters / 111_320.0, center.longitude)

    private fun branches(vararg b: BranchRef) = b.withIndex().associate { (i, ref) -> "id$i" to ref }

    @Test
    fun `branches beyond the radius are left out`() {
        val r = limitBranches(branches(at(300), at(900), at(1200), at(5000)), center)
        assertEquals(setOf("스타벅스 300m", "스타벅스 900m"), r.values.map { it.name }.toSet())
    }

    @Test
    fun `only the nearest five of a brand stay`() {
        val all = branches(at(900), at(100), at(500), at(300), at(700), at(200), at(400))
        val r = limitBranches(all, center)
        assertEquals(listOf(100, 200, 300, 400, 500), r.values.map { it.name.removePrefix("스타벅스 ").removeSuffix("m").toInt() }.sorted())
    }

    @Test
    fun `each brand gets its own five`() {
        val many = (1..8).map { at(it * 100, "메가커피") }
        val few = listOf(at(950, "올리브영"))
        val r = limitBranches(branches(*(many + few).toTypedArray()), center)
        assertEquals(5, r.values.count { it.brandKey == brandKey("메가커피") })
        assertEquals(1, r.values.count { it.brandKey == brandKey("올리브영") })
    }

    @Test
    fun `fewer than five all stay`() {
        val all = branches(at(100), at(200), at(300))
        assertEquals(all, limitBranches(all, center))
    }

    @Test
    fun `ids and order are kept`() {
        val all = branches(at(800), at(100), at(400))
        assertEquals(listOf("id0", "id1", "id2"), limitBranches(all, center).keys.toList())
    }

    @Test
    fun `without a center nothing is left out`() {
        val all = branches(at(100), at(50_000))
        assertEquals(all, limitBranches(all, null))
    }

    @Test
    fun `the limits can be changed`() {
        val all = branches(at(100), at(200), at(300), at(1500))
        val r = limitBranches(all, center, radiusM = 2000, perBrand = 2)
        assertEquals(setOf("스타벅스 100m", "스타벅스 200m"), r.values.map { it.name }.toSet())
    }

    @Test
    fun `empty input gives empty output`() {
        assertTrue(limitBranches(emptyMap(), center).isEmpty())
    }
}
