package com.capstone.snapspot.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class MotionTest {
    @Test
    fun `reduced motion drops transitions altogether`() {
        assertSame(EnterTransition.None, enterOr(reduce = true) { fadeIn() })
        assertSame(ExitTransition.None, exitOr(reduce = true) { fadeOut() })
    }

    @Test
    fun `normal motion keeps the transition`() {
        assertNotSame(EnterTransition.None, enterOr(reduce = false) { fadeIn() })
        assertNotSame(ExitTransition.None, exitOr(reduce = false) { fadeOut() })
    }

    @Test
    fun `the transition is not even built when motion is reduced`() {
        var built = 0
        enterOr(reduce = true) { built++; fadeIn() }
        exitOr(reduce = true) { built++; fadeOut() }
        assertSame(0, built)
    }
}
