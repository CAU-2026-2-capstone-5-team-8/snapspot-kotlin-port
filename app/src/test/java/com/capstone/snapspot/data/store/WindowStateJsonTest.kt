package com.capstone.snapspot.data.store

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowStateJsonTest {
    private val state = WindowState(
        at = 1_700_000_000_000,
        here = Coords(37.5446, 127.0565),
        slots = 90,
        byGroup = mapOf("place" to 2, "스타벅스" to 30),
        branches = mapOf("b|스타벅스|42" to BranchRef("스타벅스 성수역점", "스타벅스", 37.5445, 127.0553)),
        requests = 2,
    )

    @Test
    fun `a plan survives JSON`() {
        assertEquals(state, windowStateFromJson(state.toJson()))
    }

    @Test
    fun `a plan made without a known position keeps that`() {
        val noPos = state.copy(here = null, branches = emptyMap(), byGroup = emptyMap())
        assertNull(windowStateFromJson(noPos.toJson())!!.here)
        assertEquals(noPos, windowStateFromJson(noPos.toJson()))
    }

    @Test
    fun `nothing saved or unreadable content means no plan`() {
        assertNull(windowStateFromJson(null))
        assertNull(windowStateFromJson(""))
        assertNull(windowStateFromJson("garbage"))
        assertNull(windowStateFromJson("""{"at":"x"}"""))
    }
}
