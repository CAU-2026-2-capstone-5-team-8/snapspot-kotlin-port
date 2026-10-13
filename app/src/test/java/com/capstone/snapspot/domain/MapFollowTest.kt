package com.capstone.snapspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFollowTest {
    private val near = 15
    private val far = MIN_SEARCH_ZOOM - 1

    @Test
    fun `the map starts out following, with no button`() {
        val s = MapFollow()
        assertTrue(s.following)
        assertFalse(s.showSearchHere)
        assertFalse(s.zoomHint)
    }

    @Test
    fun `a move the app made itself never shows the button, however often it happens`() {
        var s = MapFollow()
        repeat(5) { s = s.cameraMoved(byUser = false, zoom = near) }
        s = s.cameraMoved(byUser = false, zoom = far)
        assertEquals(MapFollow(), s)
    }

    @Test
    fun `moving the map by hand stops following and offers the lookup`() {
        val s = MapFollow().cameraMoved(byUser = true, zoom = near)
        assertFalse(s.following)
        assertTrue(s.showSearchHere)
        assertFalse(s.zoomHint)
    }

    @Test
    fun `zooming out too far by hand asks for a closer view instead of offering the lookup`() {
        val s = MapFollow().cameraMoved(byUser = true, zoom = far)
        assertFalse(s.following)
        assertFalse(s.showSearchHere)
        assertTrue(s.zoomHint)
    }

    @Test
    fun `an app move after the user left does not hide or bring back anything`() {
        val left = MapFollow().cameraMoved(byUser = true, zoom = near)
        assertEquals(left, left.cameraMoved(byUser = false, zoom = near))
    }

    @Test
    fun `zooming back in changes the hint into the button`() {
        val s = MapFollow().cameraMoved(true, far).cameraMoved(true, near)
        assertTrue(s.showSearchHere)
        assertFalse(s.zoomHint)
    }

    @Test
    fun `back to my position follows again and hides the button`() {
        val s = MapFollow().cameraMoved(true, near).backToMe()
        assertTrue(s.following)
        assertFalse(s.showSearchHere)
        assertFalse(s.zoomHint)
        val hint = MapFollow().cameraMoved(true, far).backToMe()
        assertEquals(MapFollow(), hint)
    }

    @Test
    fun `focusing a place stops following but shows no button`() {
        val s = MapFollow().placeFocused()
        assertFalse(s.following)
        assertFalse(s.showSearchHere)
        assertFalse(s.zoomHint)
    }

    @Test
    fun `the move that focusing a place causes does not bring the button either`() {
        val s = MapFollow().placeFocused().cameraMoved(byUser = false, zoom = near)
        assertFalse(s.showSearchHere)
    }

    @Test
    fun `running the lookup hides the button but does not start following`() {
        val s = MapFollow().cameraMoved(true, near).searched()
        assertFalse(s.showSearchHere)
        assertFalse(s.following)
    }
}
