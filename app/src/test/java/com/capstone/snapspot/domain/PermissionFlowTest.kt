package com.capstone.snapspot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionFlowTest {
    private fun perms(notif: Boolean, fg: Boolean, bg: Boolean) = AlertPermissions(notif = notif, fg = fg, bg = bg)

    @Test
    fun `the first missing permission comes first, in the guide's order`() {
        assertEquals(PermissionStep.FOREGROUND_LOCATION, nextStep(perms(false, false, false), 36))
        assertEquals(PermissionStep.FOREGROUND_LOCATION, nextStep(perms(true, false, false), 36))
        assertEquals(PermissionStep.NOTIFICATIONS, nextStep(perms(false, true, false), 36))
        assertEquals(PermissionStep.NOTIFICATIONS, nextStep(perms(false, true, true), 36))
        assertEquals(PermissionStep.BACKGROUND_LOCATION, nextStep(perms(true, true, false), 36))
        assertEquals(PermissionStep.DONE, nextStep(perms(true, true, true), 36))
    }

    @Test
    fun `always location is never asked before plain location`() {
        // An inconsistent reading (bg without fg) still starts from the beginning.
        assertEquals(PermissionStep.FOREGROUND_LOCATION, nextStep(perms(true, false, true), 36))
    }

    @Test
    fun `before Android 10 there is no always-location step`() {
        assertEquals(PermissionStep.DONE, nextStep(perms(true, true, false), 28))
        assertEquals(listOf(PermissionStep.FOREGROUND_LOCATION, PermissionStep.NOTIFICATIONS), guideSteps(28))
    }

    @Test
    fun `from Android 10 the guide lists all three steps`() {
        val all = listOf(PermissionStep.FOREGROUND_LOCATION, PermissionStep.NOTIFICATIONS, PermissionStep.BACKGROUND_LOCATION)
        assertEquals(all, guideSteps(29))
        assertEquals(all, guideSteps(36))
    }

    @Test
    fun `foreground location uses a pop-up until it was refused`() {
        assertEquals(HowToAsk.DIALOG, howToAsk(PermissionStep.FOREGROUND_LOCATION, 36, denied = false))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.FOREGROUND_LOCATION, 36, denied = true))
    }

    @Test
    fun `notifications use a pop-up only from Android 13`() {
        assertEquals(HowToAsk.DIALOG, howToAsk(PermissionStep.NOTIFICATIONS, 33, denied = false))
        assertEquals(HowToAsk.DIALOG, howToAsk(PermissionStep.NOTIFICATIONS, 36, denied = false))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.NOTIFICATIONS, 32, denied = false))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.NOTIFICATIONS, 36, denied = true))
    }

    @Test
    fun `always location is a pop-up only on Android 10, settings only from Android 11`() {
        assertEquals(HowToAsk.DIALOG, howToAsk(PermissionStep.BACKGROUND_LOCATION, 29, denied = false))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.BACKGROUND_LOCATION, 29, denied = true))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.BACKGROUND_LOCATION, 30, denied = false))
        assertEquals(HowToAsk.SETTINGS, howToAsk(PermissionStep.BACKGROUND_LOCATION, 36, denied = false))
    }

    @Test
    fun `a step counts as granted from the matching permission only`() {
        val p = perms(notif = true, fg = false, bg = true)
        assertTrue(isGranted(PermissionStep.NOTIFICATIONS, p))
        assertFalse(isGranted(PermissionStep.FOREGROUND_LOCATION, p))
        assertTrue(isGranted(PermissionStep.BACKGROUND_LOCATION, p))
        assertTrue(isGranted(PermissionStep.DONE, perms(false, false, false)))
    }

    @Test
    fun `every combination on every Android version ends in DONE once everything is granted`() {
        for (sdk in listOf(26, 28, 29, 30, 32, 33, 36)) {
            for (n in listOf(false, true)) for (f in listOf(false, true)) for (b in listOf(false, true)) {
                val step = nextStep(perms(n, f, b), sdk)
                if (n && f && (b || sdk < 29)) assertEquals("sdk $sdk $n $f $b", PermissionStep.DONE, step)
                else assertTrue("sdk $sdk $n $f $b", step != PermissionStep.DONE)
            }
        }
    }
}
