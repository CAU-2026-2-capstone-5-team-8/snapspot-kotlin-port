package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords

/** The map draws only branches this close to where the user (or the map's center) is. */
const val MAP_SHOW_RADIUS_M = 1000

/** At most this many branches of one brand are drawn, the nearest ones. */
const val MAP_SHOW_PER_BRAND = 5

/**
 * The branches worth drawing around [center]: within [radiusM], and per brand only the [perBrand] nearest. Only what
 * is drawn changes, not what is looked up. Without a [center] nothing is left out, so the map is never empty just
 * because the position is unknown.
 */
fun limitBranches(
    branches: Map<String, BranchRef>,
    center: Coords?,
    radiusM: Int = MAP_SHOW_RADIUS_M,
    perBrand: Int = MAP_SHOW_PER_BRAND,
): Map<String, BranchRef> {
    if (center == null) return branches
    val keep = HashSet<String>()
    branches.entries
        .map { it to distanceMeters(center, Coords(it.value.latitude, it.value.longitude)) }
        .filter { (_, d) -> d <= radiusM }
        .groupBy { (e, _) -> e.value.brandKey }
        .values
        .forEach { group -> group.sortedBy { (_, d) -> d }.take(perBrand).forEach { (e, _) -> keep.add(e.key) } }
    return branches.filterKeys { it in keep }
}
