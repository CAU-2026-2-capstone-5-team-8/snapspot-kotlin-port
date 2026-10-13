package com.capstone.snapspot.data.db

import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.decodeDetail
import com.capstone.snapspot.domain.encodeDetail
import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PlaceEntity.toDomain() = Place(
    id, name, address, category, note, endDate, latitude, longitude, radius, imageUri, kakaoId, placeUrl, createdAt, lastNotifiedAt,
)

fun Place.toEntity() = PlaceEntity(
    id, name, address, category, note, endDate, latitude, longitude, radius, imageUri, kakaoId, placeUrl, createdAt, lastNotifiedAt,
)

fun CouponEntity.toDomain() = Coupon(id, brand, benefit, expiresAt, imageUri, createdAt, usedAt, lastNotifiedAt)

fun Coupon.toEntity() = CouponEntity(id, brand, benefit, expiresAt, imageUri, createdAt, usedAt, lastNotifiedAt)

fun LogEvent.toEntity() = EventEntity(
    t = t,
    kind = kind.wire,
    refId = id,
    detailJson = detail?.let(::encodeDetail),
)

/** Null for a kind this version does not know, so a row from a newer build is skipped, not fatal. */
fun EventEntity.toDomain(): LogEvent? {
    val k = EventKind.fromWire(kind) ?: return null
    return LogEvent(t, k, refId, decodeDetail(detailJson))
}

@Serializable
private data class BranchDto(
    val latitude: Double,
    val longitude: Double,
    val kakaoId: String,
    val name: String,
    val address: String,
)

private val json = Json

fun List<Branch>.toJson(): String =
    json.encodeToString(map { BranchDto(it.latitude, it.longitude, it.kakaoId, it.name, it.address) })

/** Unreadable cache content gives null, which the caller treats as a cache miss. */
fun branchesFromJson(raw: String): List<Branch>? =
    try {
        json.decodeFromString<List<BranchDto>>(raw).map { Branch(it.latitude, it.longitude, it.kakaoId, it.name, it.address) }
    } catch (_: Exception) {
        null
    }
