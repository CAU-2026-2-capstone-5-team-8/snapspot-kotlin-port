package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/** The map looks for branches again on its own only once the user has left this circle around the first lookup. */
const val MAP_LOAD_RADIUS_M = 1500

/** Zoom levels below this are too far out to look for branches (Kakao: a bigger number is closer). */
const val MIN_SEARCH_ZOOM = 13

enum class ZoomState { SEARCHABLE, TOO_FAR }

fun zoomState(zoom: Int): ZoomState = if (zoom >= MIN_SEARCH_ZOOM) ZoomState.SEARCHABLE else ZoomState.TOO_FAR

/** Same shape as the watch plan's region ids, so a branch found by both is drawn once. */
fun mapBranchId(brandKey: String, kakaoId: String): String = "b|$brandKey|$kakaoId"

/** [brandKeys] are the brands that were looked up, so the screen can tell when a new coupon needs another lookup. */
data class MapBranchLoad(val branches: Map<String, BranchRef>, val brandKeys: Set<String>, val requests: Int)

/** Brand key to the name as written on the coupon, for the active coupons only. */
fun activeBrands(coupons: List<Coupon>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<String, String> {
    val brands = LinkedHashMap<String, String>()
    for (c in coupons) if (isActive(c, now, zone)) brands[brandKey(c.brand)] = c.brand
    return brands
}

/**
 * The branches around [center] of every brand with an active coupon, from the cache while it is fresh. One lookup
 * per brand; a brand whose lookup fails is skipped. Used by the map, apart from the watch plan.
 */
suspend fun loadMapBranches(
    coupons: List<Coupon>,
    center: Coords,
    api: KakaoLocalApi,
    cache: BranchCache,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): MapBranchLoad {
    val brands = activeBrands(coupons, now, zone)
    val found = LinkedHashMap<String, BranchRef>()
    var requests = 0
    for ((key, brand) in brands) {
        try {
            val r = nearbyBranches(brand, center, api, cache, now)
            requests += r.requests
            for (b in r.branches) found[mapBranchId(key, b.kakaoId)] = BranchRef(b.name, key, b.latitude, b.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // skip this brand; the others still show
        }
    }
    return MapBranchLoad(found, brands.keys, requests)
}

/**
 * The badges to draw: the map's own lookup and the watch plan's branches together (the same branch once), kept only
 * for brands that still have an active coupon. A used, expired or deleted coupon takes its brand's badges away,
 * so no badge is left that opens nothing.
 */
fun visibleBranches(
    mapBranches: Map<String, BranchRef>,
    windowBranches: Map<String, BranchRef>,
    coupons: List<Coupon>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<String, BranchRef> {
    val live = activeBrands(coupons, now, zone).keys
    if (live.isEmpty()) return emptyMap()
    return (windowBranches + mapBranches).filterValues { it.brandKey in live }
}

/** Places the map draws: those that have ended are left out (they stay in the saved list). */
fun mapPlaces(places: List<Place>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Place> =
    places.filter { suppressReason(it, now, zone) != SuppressReason.ENDED }

/** True once [here] is outside the circle around the place of the first lookup. */
fun leftLoadedArea(here: Coords, loadedAt: Coords, radiusM: Int = MAP_LOAD_RADIUS_M): Boolean =
    distanceMeters(here, loadedAt) > radiusM
