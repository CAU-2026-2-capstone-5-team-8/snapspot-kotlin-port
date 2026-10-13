package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.Plan
import com.capstone.snapspot.domain.model.Spot
import com.capstone.snapspot.domain.model.WindowState
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/** OS limits: Android monitors at most 100 regions, iOS 20. Keep a margin on Android. */
const val MAX_REGIONS = 90

/** Branch alert radius. Geofence alerts arrive minutes late, so err on the wide side. */
const val BRANCH_RADIUS_M = 200

/** Leaving this circle around the user triggers a re-plan. */
const val REFRESH_RADIUS_M = 1000

const val PLACE_GROUP = "place"

fun isBranchRegion(id: String): Boolean = id.startsWith("b|")

data class WindowSummary(val active: Int, val byGroup: Map<String, Int>, val requests: Int)

data class BuiltWindow(val plan: Plan, val state: WindowState, val summary: WindowSummary)

/**
 * Plans which regions to register around [here]: saved places that have not ended, plus the nearby
 * branches of every brand with an active coupon, sharing [maxRegions] fairly. Registering the plan
 * with the OS is the caller's job. A brand whose branch lookup fails is skipped, not fatal.
 */
suspend fun buildWindow(
    places: List<Place>,
    coupons: List<Coupon>,
    here: Coords?,
    api: KakaoLocalApi,
    cache: BranchCache,
    now: Long = System.currentTimeMillis(),
    maxRegions: Int = MAX_REGIONS,
    zone: ZoneId = ZoneId.systemDefault(),
): BuiltWindow {
    val spots = places
        .filter { suppressReason(it, now, zone) != SuppressReason.ENDED }
        .map { Spot(it.id, PLACE_GROUP, it.latitude, it.longitude, it.radius) }
        .toMutableList()

    val branchRefs = LinkedHashMap<String, BranchRef>()
    var requests = 0
    if (here != null) {
        // One lookup per brand, however many coupons it has or how it is spaced.
        val brands = LinkedHashMap<String, String>()
        for (c in coupons.filter { isActive(it, now, zone) }) brands[brandKey(c.brand)] = c.brand
        for ((key, brand) in brands) {
            try {
                val r = nearbyBranches(brand, here, api, cache, now)
                requests += r.requests
                for (b in r.branches) {
                    val id = "b|$key|${b.kakaoId}"
                    branchRefs[id] = BranchRef(b.name, key, b.latitude, b.longitude)
                    spots += Spot(id, key, b.latitude, b.longitude, BRANCH_RADIUS_M)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // skip this brand; the others still get their slots
            }
        }
    }

    val plan = planRegions(here, spots, maxRegions, REFRESH_RADIUS_M)
    val registered = plan.regions.map { it.identifier }.toSet()
    val state = WindowState(
        at = now,
        here = here,
        slots = maxRegions,
        byGroup = plan.byGroup,
        branches = branchRefs.filterKeys { it in registered },
        requests = requests,
    )
    val summary = WindowSummary(
        active = plan.regions.count { it.identifier != REFRESH_ID },
        byGroup = plan.byGroup,
        requests = requests,
    )
    return BuiltWindow(plan, state, summary)
}
