package com.capstone.snapspot.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.data.db.BranchCacheEntity
import com.capstone.snapspot.data.db.EventEntity
import com.capstone.snapspot.data.db.SnapSpotDatabase
import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.repo.RoomBranchCache
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.domain.CACHE_TTL_MS
import com.capstone.snapspot.domain.CachedBranches
import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.LogEvent
import com.capstone.snapspot.domain.model.Branch
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.domain.parseLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var db: SnapSpotDatabase
    private lateinit var places: PlaceRepository
    private lateinit var coupons: CouponRepository
    private lateinit var events: EventLogRepository
    private lateinit var cache: RoomBranchCache

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SnapSpotDatabase::class.java).build()
        places = PlaceRepository(db.places())
        coupons = CouponRepository(db.coupons())
        events = EventLogRepository(db.events())
        cache = RoomBranchCache(db.branchCache())
    }

    @After
    fun close() {
        db.close()
    }

    private fun place(id: String, createdAt: Long, name: String = id) = Place(
        id = id, name = name, address = "", category = "카페", note = "", endDate = null,
        latitude = 37.5, longitude = 127.0, radius = 200, imageUri = null, createdAt = createdAt, lastNotifiedAt = null,
    )

    private fun coupon(id: String, createdAt: Long = 0) = Coupon(id, "스타벅스", "아메리카노", null, null, createdAt, null, null)

    // --- places ---

    @Test
    fun placesComeBackNewestFirst() = runBlocking {
        places.save(place("old", 1))
        places.save(place("new", 3))
        places.save(place("mid", 2))
        assertEquals(listOf("new", "mid", "old"), places.getAll().map { it.id })
        assertEquals(listOf("new", "mid", "old"), places.observeAll().first().map { it.id })
    }

    @Test
    fun savingAgainReplacesThePlaceInsteadOfDuplicating() = runBlocking {
        places.save(place("a", 1, name = "before"))
        places.save(place("a", 1, name = "after"))
        assertEquals(listOf("after"), places.getAll().map { it.name })
    }

    @Test
    fun removeAndMarkNotified() = runBlocking {
        places.save(place("a", 1))
        places.save(place("b", 2))
        places.markNotified("a", 777)
        assertEquals(777L, places.get("a")!!.lastNotifiedAt)
        assertNull(places.get("b")!!.lastNotifiedAt)
        places.remove("a")
        assertNull(places.get("a"))
        assertEquals(1, places.getAll().size)
    }

    // --- coupons ---

    @Test
    fun markUsedTouchesOnlyThatCoupon() = runBlocking {
        coupons.save(coupon("a"))
        coupons.save(coupon("b"))
        coupons.markUsed("a", 5)
        assertEquals(5L, coupons.get("a")!!.usedAt)
        assertNull(coupons.get("b")!!.usedAt)
    }

    @Test
    fun markNotifiedCoversTheListedCouponsOnly() = runBlocking {
        coupons.save(coupon("a"))
        coupons.save(coupon("b"))
        coupons.save(coupon("c"))
        coupons.markNotified(listOf("a", "c"), 9)
        assertEquals(mapOf("a" to 9L, "b" to null, "c" to 9L), coupons.getAll().associate { it.id to it.lastNotifiedAt })
        coupons.markNotified(emptyList(), 1) // nothing listed: nothing changes, and no invalid SQL
        assertEquals(9L, coupons.get("a")!!.lastNotifiedAt)
    }

    @Test
    fun couponsComeBackNewestFirstAndCanBeRemoved() = runBlocking {
        coupons.save(coupon("a", 1))
        coupons.save(coupon("b", 2))
        assertEquals(listOf("b", "a"), coupons.observeAll().first().map { it.id })
        coupons.remove("b")
        assertEquals(listOf("a"), coupons.getAll().map { it.id })
    }

    // --- event log ---

    private fun ev(t: Long) = LogEvent(t, EventKind.REPLAN, detail = mapOf("n" to t))

    @Test
    fun theLogKeepsInsertionOrderAndDropsTheOldestPastTheCap() = runBlocking {
        for (t in 1L..5L) events.append(ev(t), max = 3)
        assertEquals(listOf(3L, 4L, 5L), events.getAll().map { it.t })
    }

    @Test
    fun theDefaultCapIs500() = runBlocking {
        for (t in 1L..503L) events.append(ev(t))
        val all = events.getAll()
        assertEquals(500, all.size)
        assertEquals(4L, all.first().t)
        assertEquals(503L, all.last().t)
    }

    @Test
    fun clearEmptiesTheLog() = runBlocking {
        events.append(ev(1))
        events.clear()
        assertTrue(events.getAll().isEmpty())
    }

    @Test
    fun theExportReadsBackThroughTheSameParserAsTheReactNativeLog() = runBlocking {
        events.append(LogEvent(10, EventKind.NOTIFY_SUPPRESSED, "p1", mapOf("reason" to "최근에 알림")))
        events.append(LogEvent(11, EventKind.GEOFENCE_ENTER, "b|스타벅스|1"))
        val back = parseLog(events.exportJson())
        assertEquals(listOf(10L, 11L), back.map { it.t })
        assertEquals("최근에 알림", back[0].detail!!["reason"])
        assertEquals("b|스타벅스|1", back[1].id)
    }

    @Test
    fun aRowOfAnUnknownKindIsSkippedWhenReading() = runBlocking {
        db.events().appendCapped(EventEntity(t = 1, kind = "from_the_future", refId = null, detailJson = null), 500)
        events.append(ev(2))
        assertEquals(listOf(2L), events.getAll().map { it.t })
    }

    // --- branch cache ---

    private val branches = listOf(Branch(37.5445, 127.0553, "1", "스타벅스 성수역점", "서울 성동구"))

    @Test
    fun cachedBranchesComeBack() = runBlocking {
        assertNull(cache.get("스타벅스|3754:12705"))
        cache.set("스타벅스|3754:12705", CachedBranches(1_000, branches))
        assertEquals(CachedBranches(1_000, branches), cache.get("스타벅스|3754:12705"))
    }

    @Test
    fun anEntryPastItsLifetimeIsRemovedWhenANewOneIsWritten() = runBlocking {
        cache.set("old", CachedBranches(1_000, branches))
        cache.set("fresh", CachedBranches(1_000 + CACHE_TTL_MS + 1, branches))
        assertNull(db.branchCache().get("old"))
        assertTrue(db.branchCache().get("fresh") != null)
    }

    @Test
    fun aCorruptCacheRowIsAMissNotACrash() = runBlocking {
        db.branchCache().upsert(BranchCacheEntity("bad", 1, "not json"))
        assertNull(cache.get("bad"))
    }

    // --- window state (DataStore) ---

    @Test
    fun theWindowStateIsSavedAndReadBack() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File.createTempFile("window", ".preferences_pb").also { it.delete() }
        try {
            val repo = WindowRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertNull(repo.load())
            val state = WindowState(
                at = 123, here = Coords(37.5, 127.0), slots = 90, byGroup = mapOf("place" to 1),
                branches = mapOf("b|스타벅스|1" to BranchRef("스타벅스 성수역점", "스타벅스", 37.5, 127.0)), requests = 1,
            )
            repo.save(state)
            assertEquals(state, repo.load())
            repo.save(state.copy(at = 456))
            assertEquals(456L, repo.observe().first()!!.at)
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
