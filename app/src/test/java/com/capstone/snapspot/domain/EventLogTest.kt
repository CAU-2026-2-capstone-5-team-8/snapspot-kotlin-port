package com.capstone.snapspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    private fun ev(t: Long) = LogEvent(t, EventKind.REPLAN)

    @Test
    fun `appendCapped keeps the newest events and does not mutate its input`() {
        val list = listOf(ev(1), ev(2), ev(3))
        val next = appendCapped(list, ev(4), 3)
        assertEquals(listOf(2L, 3L, 4L), next.map { it.t })
        assertEquals(3, list.size)
    }

    @Test
    fun `appendCapped below the cap just appends`() {
        assertEquals(listOf(1L, 2L), appendCapped(listOf(ev(1)), ev(2), 5).map { it.t })
    }

    @Test
    fun `parseLog tolerates empty, corrupt, and non-array input`() {
        assertEquals(emptyList<LogEvent>(), parseLog(null))
        assertEquals(emptyList<LogEvent>(), parseLog(""))
        assertEquals(emptyList<LogEvent>(), parseLog("not json"))
        assertEquals(emptyList<LogEvent>(), parseLog("{\"t\":1}"))
    }

    @Test
    fun `parseLog drops entries with a bad timestamp or unknown kind`() {
        val raw = """
            [
              {"t":1,"kind":"replan"},
              {"t":"x","kind":"replan"},
              {"t":2,"kind":"something_else"},
              null,
              {"t":3,"kind":"notify_shown","id":"p1"}
            ]
        """.trimIndent()
        assertEquals(listOf(1L, 3L), parseLog(raw).map { it.t })
    }

    @Test
    fun `an exported log reads back the same, in the React Native shape`() {
        val events = listOf(
            LogEvent(10, EventKind.NOTIFY_SUPPRESSED, "p1", mapOf("reason" to "최근에 알림")),
            LogEvent(11, EventKind.NOTIFY_SHOWN, "b|스타벅스|1", mapOf("type" to "branch", "forced" to false, "branch" to null)),
            LogEvent(12, EventKind.REPLAN, detail = mapOf("slots" to 90L)),
        )
        val json = encodeLog(events)
        assertEquals(events, parseLog(json))
        // Field names and the snake_case kind are what scripts/metrics.ts reads.
        assertTrue(json.contains("\"kind\":\"notify_suppressed\""))
    }
}
