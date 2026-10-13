package com.capstone.snapspot.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Newest first, like the React Native list (a new place goes to the front). */
@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: String): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE places SET lastNotifiedAt = :at WHERE id = :id")
    suspend fun markNotified(id: String, at: Long)
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CouponEntity>>

    @Query("SELECT * FROM coupons ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<CouponEntity>

    @Query("SELECT * FROM coupons WHERE id = :id")
    suspend fun get(id: String): CouponEntity?

    @Upsert
    suspend fun upsert(coupon: CouponEntity)

    @Query("DELETE FROM coupons WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE coupons SET usedAt = :at WHERE id = :id")
    suspend fun markUsed(id: String, at: Long)

    @Query("UPDATE coupons SET lastNotifiedAt = :at WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>, at: Long)
}

@Dao
abstract class EventDao {
    @Query("SELECT * FROM events ORDER BY rowId ASC")
    abstract suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY rowId ASC")
    abstract fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events")
    abstract suspend fun count(): Int

    @androidx.room.Insert
    protected abstract suspend fun insert(event: EventEntity)

    /** Oldest events fall off first. */
    @Query("DELETE FROM events WHERE rowId NOT IN (SELECT rowId FROM events ORDER BY rowId DESC LIMIT :max)")
    protected abstract suspend fun trimTo(max: Int)

    @Query("DELETE FROM events")
    abstract suspend fun clear()

    @Transaction
    open suspend fun appendCapped(event: EventEntity, max: Int) {
        insert(event)
        trimTo(max)
    }
}

@Dao
interface BranchCacheDao {
    @Query("SELECT * FROM branch_cache WHERE `key` = :key")
    suspend fun get(key: String): BranchCacheEntity?

    @Upsert
    suspend fun upsert(entry: BranchCacheEntity)

    @Query("DELETE FROM branch_cache WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
