package com.capstone.snapspot.domain

import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Coords
import kotlin.coroutines.cancellation.CancellationException

/**
 * Branches of [brand] around the user, nearest first, for the preview shown before saving a coupon.
 * Null when the position or brand is unknown, or when the lookup fails.
 */
suspend fun previewBranches(brand: String, here: Coords?, api: KakaoLocalApi, cache: BranchCache): List<Branch>? {
    if (here == null || brand.isEmpty()) return null
    return try {
        nearbyBranches(brand, here, api, cache).branches.sortedBy { distanceMeters(here, Coords(it.latitude, it.longitude)) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
