package com.capstone.snapspot.data.repo

import com.capstone.snapspot.data.db.BranchCacheDao
import com.capstone.snapspot.data.db.BranchCacheEntity
import com.capstone.snapspot.data.db.CouponDao
import com.capstone.snapspot.data.db.EventDao
import com.capstone.snapspot.data.db.PlaceDao
import com.capstone.snapspot.data.db.branchesFromJson
import com.capstone.snapspot.data.db.toDomain
import com.capstone.snapspot.data.db.toEntity
import com.capstone.snapspot.data.db.toJson
import com.capstone.snapspot.domain.BranchCache
import com.capstone.snapspot.domain.CACHE_TTL_MS
import com.capstone.snapspot.domain.CachedBranches
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.MAX_EVENTS
import com.capstone.snapspot.domain.encodeLog
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Saved places. Screens watch [observeAll]; geofence receivers use the suspend calls. */
@Singleton
class PlaceRepository @Inject constructor(private val dao: PlaceDao) {
    fun observeAll(): Flow<List<Place>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<Place> = dao.getAll().map { it.toDomain() }

    suspend fun get(id: String): Place? = dao.get(id)?.toDomain()

    suspend fun save(place: Place) = dao.upsert(place.toEntity())

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun markNotified(id: String, at: Long) = dao.markNotified(id, at)
}

@Singleton
class CouponRepository @Inject constructor(private val dao: CouponDao) {
    fun observeAll(): Flow<List<Coupon>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<Coupon> = dao.getAll().map { it.toDomain() }

    suspend fun get(id: String): Coupon? = dao.get(id)?.toDomain()

    suspend fun save(coupon: Coupon) = dao.upsert(coupon.toEntity())

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun markUsed(id: String, at: Long) = dao.markUsed(id, at)

    /** After an alert: every coupon it listed counts as notified, so the brand cools down together. */
    suspend fun markNotified(ids: List<String>, at: Long) {
        if (ids.isNotEmpty()) dao.markNotified(ids, at)
    }
}

/** Field-measurement log: geofence events, re-plans, and alerts shown or held back. */
@Singleton
class EventLogRepository @Inject constructor(private val dao: EventDao) {
    fun observeAll(): Flow<List<LogEvent>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun getAll(): List<LogEvent> = dao.getAll().mapNotNull { it.toDomain() }

    /** Never fails: a logging problem must not stop an alert. Cancellation still propagates. */
    suspend fun append(event: LogEvent, max: Int = MAX_EVENTS) {
        try {
            dao.appendCapped(event.toEntity(), max)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // dropped on purpose
        }
    }

    suspend fun clear() = dao.clear()

    /** The whole log as JSON in the same shape the React Native app exported. */
    suspend fun exportJson(): String = encodeLog(getAll())
}

/** Branch search results kept for a few hours, per brand and grid cell. */
@Singleton
class RoomBranchCache @Inject constructor(private val dao: BranchCacheDao) : BranchCache {
    override suspend fun get(key: String): CachedBranches? {
        val row = dao.get(key) ?: return null
        val branches = branchesFromJson(row.branchesJson) ?: return null
        return CachedBranches(row.at, branches)
    }

    override suspend fun set(key: String, value: CachedBranches) {
        dao.upsert(BranchCacheEntity(key, value.at, value.branches.toJson()))
        // Entries past their lifetime are never read again, so drop them as we go.
        dao.deleteOlderThan(value.at - CACHE_TTL_MS)
    }
}
