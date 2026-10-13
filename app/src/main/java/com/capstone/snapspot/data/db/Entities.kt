package com.capstone.snapspot.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val category: String,
    val note: String,
    val endDate: String?,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val imageUri: String?,
    val kakaoId: String?,
    val placeUrl: String?,
    val createdAt: Long,
    val lastNotifiedAt: Long?,
)

@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val benefit: String,
    val expiresAt: String?,
    val imageUri: String?,
    val createdAt: Long,
    val usedAt: Long?,
    val lastNotifiedAt: Long?,
)

/** One line of the field-measurement log. Insertion order ([rowId]) is the log order. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val t: Long,
    val kind: String,
    val refId: String?,
    val detailJson: String?,
)

/** Branch search results for one brand and grid cell ("<brandKey>|<cell>"), as JSON. */
@Entity(tableName = "branch_cache")
data class BranchCacheEntity(
    @PrimaryKey val key: String,
    val at: Long,
    val branchesJson: String,
)
