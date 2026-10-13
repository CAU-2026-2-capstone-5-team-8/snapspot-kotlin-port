package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Plan
import com.capstone.snapspot.domain.model.Region
import com.capstone.snapspot.domain.model.Spot

/**
 * Sliding geofence window. The OS watches at most ~100 regions (Android) / 20 (iOS), but a
 * franchise coupon can match hundreds of branches. So we register only the nearest spots, shared
 * fairly between groups (saved places, each brand), plus one large exit-only "refresh" region
 * around the user. Leaving it wakes the app to re-plan around the new position, so no continuous
 * location tracking is needed.
 */
const val REFRESH_ID = "refresh"

/**
 * @param slots total regions the OS allows us; the refresh region counts toward it.
 */
fun planRegions(here: Coords?, spots: List<Spot>, slots: Int, refreshRadius: Int): Plan {
    val regions = mutableListOf<Region>()
    if (here != null && slots > 0) {
        regions += Region(
            identifier = REFRESH_ID,
            latitude = here.latitude,
            longitude = here.longitude,
            radius = refreshRadius,
            notifyOnEnter = false,
            notifyOnExit = true,
        )
    }

    // One queue per group, nearest first (input order when the position is unknown).
    val queues = LinkedHashMap<String, ArrayDeque<Spot>>()
    for (s in spots) queues.getOrPut(s.group) { ArrayDeque() }.addLast(s)
    if (here != null) {
        for ((group, q) in queues) {
            val sorted = q.sortedBy { distanceMeters(here, Coords(it.latitude, it.longitude)) }
            queues[group] = ArrayDeque(sorted)
        }
    }

    // Round-robin across groups so one brand with many branches cannot take every slot.
    val byGroup = LinkedHashMap<String, Int>()
    val order = queues.keys.toList()
    var progressed = true
    while (regions.size < slots && progressed) {
        progressed = false
        for (g in order) {
            if (regions.size >= slots) break
            val next = queues.getValue(g).removeFirstOrNull() ?: continue
            progressed = true
            byGroup[g] = (byGroup[g] ?: 0) + 1
            regions += Region(
                identifier = next.id,
                latitude = next.latitude,
                longitude = next.longitude,
                radius = next.radius,
                notifyOnEnter = true,
                notifyOnExit = false,
            )
        }
    }
    return Plan(regions, byGroup)
}
