package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.KakaoDoc

/**
 * Which Kakao search results are branches of a franchise.
 *
 * Kakao puts the franchise at the end of category_name for chain stores
 * ("음식점 > 카페 > 커피전문점 > 스타벅스"), which separates real branches from shops that only
 * mention the brand ("하파크리스틴 올리브영N성수점" is a lens shop inside an Olive Young).
 */

/** How alike the coupon's brand and a category tail must be ("메가커피" vs "메가MGC커피" is about 0.44). */
private const val MIN_BRAND_SIMILARITY = 0.4

fun brandKey(brand: String): String = normalizeName(brand)

private fun tail(doc: KakaoDoc): String = doc.categoryName.split(">").last().trim()

/**
 * The category tail that names this brand, if Kakao has one: it must resemble the brand,
 * and the shops filed under it must actually be named after it (rules out "제과,베이커리").
 */
private fun brandCategory(brand: String, docs: List<KakaoDoc>): String? {
    val groups = LinkedHashMap<String, MutableList<KakaoDoc>>()
    for (d in docs) {
        val t = tail(d)
        if (t.isNotEmpty()) groups.getOrPut(t) { mutableListOf() }.add(d)
    }
    var best: Pair<String, Int>? = null
    for ((t, members) in groups) {
        if (nameSimilarity(brand, t) < MIN_BRAND_SIMILARITY) continue
        val key = normalizeName(t)
        val named = members.count { normalizeName(it.placeName).startsWith(key) }
        if (named * 2 < members.size) continue
        if (best == null || members.size > best.second) best = t to members.size
    }
    return best?.first
}

/** Branches of [brand] among [docs], in the given order. */
fun pickBranches(brand: String, docs: List<KakaoDoc>): List<KakaoDoc> {
    val cat = brandCategory(brand, docs)
    if (cat != null) return docs.filter { tail(it) == cat }
    val key = brandKey(brand)
    return if (key.isNotEmpty()) docs.filter { normalizeName(it.placeName).startsWith(key) } else emptyList()
}
