package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Extracted
import com.capstone.snapspot.domain.model.Kind

/**
 * Reads a screenshot into a place or a coupon. Gemini is the first implementation; an OCR-based one
 * can replace it later without touching matching, coupons, geofences or screens, which only ever
 * see [Extracted].
 */
interface ScreenshotAnalyzer {
    /** @param jpeg the screenshot as JPEG bytes. */
    suspend fun analyze(jpeg: ByteArray): Extracted
}

/** What an analyzer produced before cleaning; any field may be missing or blank. */
data class RawExtraction(
    val kind: String? = null,
    val brand: String? = null,
    val benefit: String? = null,
    val isPlace: Boolean? = null,
    val name: String? = null,
    val address: String? = null,
    val category: String? = null,
    val note: String? = null,
    val endDate: String? = null,
)

private val DATE_SHAPE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** True when [s] has the shape YYYY-MM-DD. It does not check that the date exists. */
fun looksLikeDate(s: String): Boolean = DATE_SHAPE.matches(s)

/**
 * Cleans a raw result so every analyzer returns the same shape: trimmed text, a place unless it says
 * "coupon", category "기타" when blank, and an end date only when it looks like YYYY-MM-DD.
 * A date that has the shape but is not real (2026-13-45) is kept; the rules treat it as no expiry.
 */
fun sanitize(raw: RawExtraction): Extracted = Extracted(
    kind = if (raw.kind == "coupon") Kind.COUPON else Kind.PLACE,
    brand = raw.brand.orEmpty().trim(),
    benefit = raw.benefit.orEmpty().trim(),
    isPlace = raw.isPlace == true,
    name = raw.name.orEmpty().trim(),
    address = raw.address.orEmpty().trim(),
    category = (raw.category ?: "기타").trim().ifEmpty { "기타" },
    note = raw.note.orEmpty().trim(),
    endDate = raw.endDate?.takeIf { DATE_SHAPE.matches(it) },
)
