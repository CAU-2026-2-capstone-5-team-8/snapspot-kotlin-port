package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.PlaceQuery

/** Below this score the top result is shown as a guess, not a confident match. */
const val CONFIDENT = 0.7

/** An exact address is solid for location even though we cannot confirm the shop itself. */
private const val ADDRESS_ONLY_SCORE = 0.65

/**
 * Candidates sorted by confidence (best first). Searches "district + name" and the name alone,
 * shortening the name word by word until a confident match shows up, then the address itself
 * (gives coordinates even when the shop is not listed).
 */
suspend fun searchPlace(ex: PlaceQuery, api: KakaoLocalApi): List<Candidate> {
    val region = regionHint(ex.address)

    val seen = LinkedHashMap<String, KakaoDoc>()
    for (name in nameVariants(ex.name)) {
        val queries = listOf(listOf(region, name).filter { it.isNotEmpty() }.joinToString(" "), name).distinct()
        for (query in queries) {
            for (d in api.keyword(query, size = 10)) seen.putIfAbsent(d.id, d)
        }
        // Any hit is not enough: "더현대 서울 B1" matches unrelated "B1" shops. Keep shortening until one fits.
        if (seen.values.any { scoreCandidate(it, ex) >= CONFIDENT }) break
    }
    val out = seen.values.map { d ->
        Candidate(
            kakaoId = d.id,
            name = d.placeName,
            address = d.roadAddressName.ifEmpty { d.addressName },
            category = d.categoryName.split(" > ").last(),
            latitude = d.y.toDouble(),
            longitude = d.x.toDouble(),
            placeUrl = d.placeUrl.ifEmpty { null },
            score = scoreCandidate(d, ex),
            source = CandidateSource.KEYWORD,
        )
    }.toMutableList()

    if (ex.address.isNotBlank()) {
        api.address(ex.address, size = 1).firstOrNull()?.let { a ->
            out += Candidate(
                kakaoId = null,
                name = ex.name.ifEmpty { a.addressName },
                address = a.roadAddressName?.takeIf { it.isNotEmpty() } ?: a.addressName,
                category = "",
                latitude = a.y.toDouble(),
                longitude = a.x.toDouble(),
                placeUrl = null,
                score = ADDRESS_ONLY_SCORE,
                source = CandidateSource.ADDRESS,
            )
        }
    }
    return out.sortedByDescending { it.score }
}
