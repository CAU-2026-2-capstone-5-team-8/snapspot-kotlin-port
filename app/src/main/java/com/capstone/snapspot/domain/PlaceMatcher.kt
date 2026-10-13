package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.PlaceQuery
import java.util.Locale

/** Scores Kakao Local search results against what the analyzer read from the screenshot. */

/** Analyzer category -> Kakao category_group_code values that agree with it. Missing = no opinion. */
private val CATEGORY_GROUPS: Map<String, List<String>> = mapOf(
    "카페" to listOf("CE7"),
    "디저트" to listOf("CE7", "FD6"),
    "식당" to listOf("FD6"),
    "술집" to listOf("FD6"),
    "전시" to listOf("CT1"),
    "관광" to listOf("AT4", "CT1"),
)

private const val WEIGHT_NAME = 0.6
private const val WEIGHT_ADDRESS = 0.3
private const val WEIGHT_CATEGORY = 0.1

// \p{Z} and \s together cover the Unicode whitespace that JS `\s` matches (Java's `\s` is ASCII only).
private val NAME_NOISE = Regex("[\\s\\p{Z}\\p{P}\\p{S}]+")
private val ADDRESS_SPLIT = Regex("[\\s\\p{Z},]+")
private val WHITESPACE = Regex("[\\s\\p{Z}]+")
private val DISTRICT_SUFFIX = Regex("(구|동|군|읍|면)$")

fun normalizeName(s: String): String =
    s.lowercase(Locale.ROOT).replace(NAME_NOISE, "").removeSuffix("점")

private fun bigrams(s: String): List<String> = (0 until s.length - 1).map { s.substring(it, it + 2) }

/** Dice coefficient on character bigrams; containment of one name in the other counts as strong. */
fun nameSimilarity(a: String, b: String): Double {
    val x = normalizeName(a)
    val y = normalizeName(b)
    if (x.isEmpty() || y.isEmpty()) return 0.0
    if (x == y) return 1.0
    val (short, long) = if (x.length <= y.length) x to y else y to x
    if (short.length >= 2 && long.contains(short)) return 0.9
    val bx = bigrams(x)
    val by = bigrams(y)
    if (bx.isEmpty() || by.isEmpty()) return 0.0
    // Each bigram of the other name can be matched once.
    val pool = by.toMutableList()
    var hits = 0
    for (g in bx) {
        val i = pool.indexOf(g)
        if (i >= 0) {
            hits++
            pool.removeAt(i)
        }
    }
    return 2.0 * hits / (bx.size + by.size)
}

private val ADDRESS_NOISE = setOf("서울", "서울시", "서울특별시", "대한민국")

private fun addressTokens(address: String): List<String> =
    address.split(ADDRESS_SPLIT).map { it.trim() }.filter { it.isNotEmpty() && it !in ADDRESS_NOISE }

/** Share of the screenshot's address tokens found in the candidate's road or lot address. null = no address to compare. */
fun addressSimilarity(extracted: String, doc: KakaoDoc): Double? {
    val tokens = addressTokens(extracted)
    if (tokens.isEmpty()) return null
    val hay = "${doc.roadAddressName} ${doc.addressName}".replace(WHITESPACE, " ")
    val found = tokens.count { hay.contains(it) }
    return found.toDouble() / tokens.size
}

fun categorySimilarity(category: String, doc: KakaoDoc): Double? {
    val groups = CATEGORY_GROUPS[category]
    if (groups == null || doc.categoryGroupCode.isEmpty()) return null
    return if (doc.categoryGroupCode in groups) 1.0 else 0.0
}

/** 0..1 confidence that [doc] is the place in the screenshot. Signals with no data are left out, not counted as 0. */
fun scoreCandidate(doc: KakaoDoc, ex: PlaceQuery): Double {
    val parts = listOf(
        WEIGHT_NAME to nameSimilarity(ex.name, doc.placeName),
        WEIGHT_ADDRESS to addressSimilarity(ex.address, doc),
        WEIGHT_CATEGORY to categorySimilarity(ex.category, doc),
    )
    var sum = 0.0
    var weight = 0.0
    for ((w, v) in parts) {
        if (v == null) continue
        sum += w * v
        weight += w
    }
    return if (weight > 0) sum / weight else 0.0
}

/**
 * "더현대 서울 B1 이벤트홀" -> itself, "더현대 서울 B1", "더현대 서울", "더현대".
 * Screenshot names often carry floors/halls/event titles that Kakao does not index.
 */
fun nameVariants(name: String): List<String> {
    val words = name.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    return (words.size downTo 1).map { n -> words.take(n).joinToString(" ") }
}

/** "서울 성동구 아차산로9길 8" -> "성동구": a district/neighborhood to narrow a keyword search. */
fun regionHint(address: String): String =
    addressTokens(address).firstOrNull { DISTRICT_SUFFIX.containsMatchIn(it) } ?: ""
