package com.capstone.snapspot.domain.model

data class Coords(val latitude: Double, val longitude: Double)

data class Place(
    val id: String,
    val name: String,
    val address: String,
    val category: String,
    val note: String,
    /** YYYY-MM-DD. Pop-ups and exhibitions end; after this date we stop alerting. */
    val endDate: String?,
    val latitude: Double,
    val longitude: Double,
    /** Geofence radius in meters. */
    val radius: Int,
    val imageUri: String?,
    /** Set when the place was matched through Kakao Local search. */
    val kakaoId: String? = null,
    val placeUrl: String? = null,
    val createdAt: Long,
    val lastNotifiedAt: Long?,
)

/** A franchise coupon: alerts at whichever branch of [brand] the user walks past. */
data class Coupon(
    val id: String,
    /** As written on the coupon, e.g. "스타벅스". Used as the Kakao search query. */
    val brand: String,
    /** What the coupon gives, e.g. "아메리카노 1+1". */
    val benefit: String,
    /** YYYY-MM-DD. Expired coupons stop alerting. */
    val expiresAt: String?,
    val imageUri: String?,
    val createdAt: Long,
    /** Set when the user marks it used; used coupons stop alerting. */
    val usedAt: Long?,
    val lastNotifiedAt: Long?,
)

enum class Kind { PLACE, COUPON }

/** What an analyzer read from a screenshot. */
data class Extracted(
    /** What the screenshot is: a place to visit, or a franchise coupon. */
    val kind: Kind,
    /** Coupons only: the franchise brand and what the coupon gives. */
    val brand: String,
    val benefit: String,
    val isPlace: Boolean,
    val name: String,
    val address: String,
    val category: String,
    val note: String,
    val endDate: String?,
)

/** The fields of a Kakao keyword-search document that we use. */
data class KakaoDoc(
    val id: String,
    val placeName: String,
    val categoryName: String,
    val categoryGroupCode: String,
    val addressName: String,
    val roadAddressName: String,
    val x: String,
    val y: String,
    val placeUrl: String,
)

/** The fields of a Kakao address-search document that we use. */
data class AddressDoc(
    val addressName: String,
    val x: String,
    val y: String,
    val roadAddressName: String?,
)

/** What the matcher compares Kakao results against. */
data class PlaceQuery(val name: String, val address: String, val category: String)

enum class CandidateSource { KEYWORD, ADDRESS }

data class Candidate(
    val kakaoId: String?,
    val name: String,
    val address: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val placeUrl: String?,
    val score: Double,
    val source: CandidateSource,
)

/** A branch of a franchise found through Kakao. */
data class Branch(
    val latitude: Double,
    val longitude: Double,
    val kakaoId: String,
    val name: String,
    val address: String,
)

/** Something worth watching: a saved place (group "place") or a branch (group = brand key). */
data class Spot(
    val id: String,
    val group: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
)

data class Region(
    val identifier: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val notifyOnEnter: Boolean,
    val notifyOnExit: Boolean,
)

data class Plan(val regions: List<Region>, val byGroup: Map<String, Int>)
