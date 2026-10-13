package com.capstone.snapspot.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private fun ratio(fg: Color, bg: Color) = contrastRatio(fg.toArgb(), bg.toArgb())

    private fun assertReadable(name: String, fg: Color, bg: Color, min: Double = MIN_TEXT_CONTRAST) {
        val r = ratio(fg, bg)
        assertTrue("$name has contrast %.2f, needs %.1f".format(r, min), r >= min)
    }

    @Test
    fun `black on white is 21 and the order does not matter`() {
        assertEquals(21.0, contrastRatio(0x000000, 0xFFFFFF), 0.001)
        assertEquals(21.0, contrastRatio(0xFFFFFF, 0x000000), 0.001)
        assertEquals(1.0, contrastRatio(0x336699, 0x336699), 0.001)
    }

    @Test
    fun `body and title ink is readable on every background it sits on`() {
        assertReadable("Ink on Paper", SnapColor.Ink, SnapColor.Paper)
        assertReadable("Ink on Surface", SnapColor.Ink, SnapColor.Surface)
        assertReadable("Ink on GreenSoft", SnapColor.Ink, SnapColor.GreenSoft)
        assertReadable("Ink on Highlight", SnapColor.Ink, SnapColor.Highlight)
    }

    @Test
    fun `small grey text is readable on paper, cards and soft green`() {
        assertReadable("Muted on Paper", SnapColor.Muted, SnapColor.Paper)
        assertReadable("Muted on Surface", SnapColor.Muted, SnapColor.Surface)
        assertReadable("Muted on GreenSoft", SnapColor.Muted, SnapColor.GreenSoft)
    }

    @Test
    fun `button and status text is readable`() {
        assertReadable("Surface on Green (primary button)", SnapColor.Surface, SnapColor.Green)
        assertReadable("Green on GreenSoft (secondary button)", SnapColor.Green, SnapColor.GreenSoft)
        assertReadable("Green on Surface", SnapColor.Green, SnapColor.Surface)
        assertReadable("Amber on AmberSoft (notice)", SnapColor.Amber, SnapColor.AmberSoft)
        assertReadable("Amber on Surface", SnapColor.Amber, SnapColor.Surface)
        assertReadable("Amber on GreenSoft", SnapColor.Amber, SnapColor.GreenSoft)
    }

    @Test
    fun `status dots and outlines that carry meaning have at least 3 to 1 against their background`() {
        assertReadable("Green dot on Surface", SnapColor.Green, SnapColor.Surface, MIN_LARGE_CONTRAST)
        assertReadable("Amber dot on Surface", SnapColor.Amber, SnapColor.Surface, MIN_LARGE_CONTRAST)
    }
}
