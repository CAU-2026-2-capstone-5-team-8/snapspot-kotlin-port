package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Coords
import kotlin.math.floor

/** Search radius around a cell center. Covers the cell plus the refresh radius. */
const val SEARCH_RADIUS_M = 2500

/** Grid cell size in degrees (~1.1km north-south, ~0.9km east-west in Seoul). */
private const val CELL_DEG = 0.01

/** Keep results briefly: branches rarely move, and Kakao's terms discourage long-term storage. */
const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

/** Kakao returns at most 45 results (3 pages x 15) per query. */
private const val MAX_PAGES = 3
private const val PAGE_SIZE = 15

data class CachedBranches(val at: Long, val branches: List<Branch>)

interface BranchCache {
    suspend fun get(key: String): CachedBranches?
    suspend fun set(key: String, value: CachedBranches)
}

fun memoryCache(): BranchCache = object : BranchCache {
    private val m = HashMap<String, CachedBranches>()
    override suspend fun get(key: String) = m[key]
    override suspend fun set(key: String, value: CachedBranches) {
        m[key] = value
    }
}

data class Cell(val key: String, val center: Coords)

fun cellOf(p: Coords): Cell {
    val i = floor(p.latitude / CELL_DEG).toLong()
    val j = floor(p.longitude / CELL_DEG).toLong()
    return Cell("$i:$j", Coords((i + 0.5) * CELL_DEG, (j + 0.5) * CELL_DEG))
}

/** [requests] counts Kakao calls made (0 on a cache hit). */
data class BranchResult(val branches: List<Branch>, val requests: Int)

/** Branches of [brand] near [here], from the cache while fresh, otherwise from Kakao. */
suspend fun nearbyBranches(
    brand: String,
    here: Coords,
    api: KakaoLocalApi,
    cache: BranchCache,
    now: Long = System.currentTimeMillis(),
): BranchResult {
    val cell = cellOf(here)
    val cacheKey = "${brandKey(brand)}|${cell.key}"
    cache.get(cacheKey)?.let { hit ->
        if (now - hit.at < CACHE_TTL_MS) return BranchResult(hit.branches, 0)
    }

    val docs = mutableListOf<com.capstone.snapspot.domain.model.KakaoDoc>()
    var requests = 0
    for (page in 1..MAX_PAGES) {
        val batch = api.keyword(
            query = brand,
            size = PAGE_SIZE,
            x = cell.center.longitude,
            y = cell.center.latitude,
            radius = SEARCH_RADIUS_M,
            sort = "distance",
            page = page,
        )
        requests++
        docs += batch
        if (batch.size < PAGE_SIZE) break
    }
    val branches = pickBranches(brand, docs).map { d ->
        Branch(
            latitude = d.y.toDouble(),
            longitude = d.x.toDouble(),
            kakaoId = d.id,
            name = d.placeName,
            address = d.roadAddressName.ifEmpty { d.addressName },
        )
    }
    cache.set(cacheKey, CachedBranches(now, branches))
    return BranchResult(branches, requests)
}
