package com.capstone.snapspot.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GeofenceHandlingTest {
    private class Recorder : GeofenceActions {
        val calls = mutableListOf<String>()

        override fun replan() {
            calls += "replan"
        }

        override suspend fun notifyPlace(placeId: String) {
            calls += "place:$placeId"
        }

        override suspend fun notifyBranch(regionId: String) {
            calls += "branch:$regionId"
        }
    }

    private suspend fun run(type: Transition, id: String): Pair<List<LogEvent>, List<String>> {
        val log = mutableListOf<LogEvent>()
        val actions = Recorder()
        handleGeofence(type, id, now = 42, log = { log += it }, actions = actions)
        return log to actions.calls
    }

    @Test
    fun `entering a saved place alerts for it`() = runTest {
        val (log, calls) = run(Transition.ENTER, "p_abc")
        assertEquals(listOf("place:p_abc"), calls)
        assertEquals(LogEvent(42, EventKind.GEOFENCE_ENTER, "p_abc"), log.single())
    }

    @Test
    fun `entering a branch alerts with the brand's coupons`() = runTest {
        val (log, calls) = run(Transition.ENTER, "b|스타벅스|123")
        assertEquals(listOf("branch:b|스타벅스|123"), calls)
        assertEquals(EventKind.GEOFENCE_ENTER, log.single().kind)
    }

    @Test
    fun `leaving the refresh circle plans again`() = runTest {
        val (log, calls) = run(Transition.EXIT, REFRESH_ID)
        assertEquals(listOf("replan"), calls)
        assertEquals(LogEvent(42, EventKind.GEOFENCE_EXIT, REFRESH_ID), log.single())
    }

    @Test
    fun `entering the refresh circle does nothing but is still logged`() = runTest {
        val (log, calls) = run(Transition.ENTER, REFRESH_ID)
        assertEquals(emptyList<String>(), calls)
        assertEquals(EventKind.GEOFENCE_ENTER, log.single().kind)
    }

    @Test
    fun `leaving a place or branch does not alert but is logged`() = runTest {
        for (id in listOf("p_abc", "b|스타벅스|123")) {
            val (log, calls) = run(Transition.EXIT, id)
            assertEquals(emptyList<String>(), calls)
            assertEquals(EventKind.GEOFENCE_EXIT, log.single().kind)
        }
    }

    @Test
    fun `the event is logged before anything else happens`() = runTest {
        val order = mutableListOf<String>()
        val actions = object : GeofenceActions {
            override fun replan() = Unit

            override suspend fun notifyPlace(placeId: String) {
                order += "alert"
            }

            override suspend fun notifyBranch(regionId: String) = Unit
        }
        handleGeofence(Transition.ENTER, "p1", 1, log = { order += "log" }, actions = actions)
        assertEquals(listOf("log", "alert"), order)
    }
}
