package com.capstone.snapspot.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaceEntity::class, CouponEntity::class, EventEntity::class, BranchCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SnapSpotDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun coupons(): CouponDao
    abstract fun events(): EventDao
    abstract fun branchCache(): BranchCacheDao
}
