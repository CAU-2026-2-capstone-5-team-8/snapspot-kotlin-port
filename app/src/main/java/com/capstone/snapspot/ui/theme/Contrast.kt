package com.capstone.snapspot.ui.theme

import kotlin.math.pow

/** WCAG relative luminance of an opaque colour given as 0xRRGGBB (alpha is ignored). */
fun relativeLuminance(rgb: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((rgb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** WCAG contrast ratio, from 1 (same colour) to 21 (black on white). Order of the two colours does not matter. */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** WCAG AA: 4.5 for normal text, 3 for large text (18sp regular / 14sp bold and up) and icons. */
const val MIN_TEXT_CONTRAST = 4.5
const val MIN_LARGE_CONTRAST = 3.0
