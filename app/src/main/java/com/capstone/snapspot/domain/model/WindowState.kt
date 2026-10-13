package com.capstone.snapspot.domain.model

/** A branch region in the window, so an Enter event can name the branch. */
data class BranchRef(
    val name: String,
    val brandKey: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * The geofence plan currently registered with the OS: read by alerts woken in the background and by
 * the status screen.
 */
data class WindowState(
    val at: Long,
    val here: Coords?,
    val slots: Int,
    /** Regions registered per group: "place" or a brand key. */
    val byGroup: Map<String, Int>,
    /** Branch regions by identifier ("b|<brandKey>|<kakaoId>"). */
    val branches: Map<String, BranchRef>,
    /** Kakao requests made by this plan (0 when every brand came from cache). */
    val requests: Int,
)
