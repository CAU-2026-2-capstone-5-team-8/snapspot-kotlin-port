package com.capstone.snapspot.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.data.db.SnapSpotDatabase
import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.repo.RoomBranchCache
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.data.sync.WindowSync
import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.KakaoLocalApi
import com.capstone.snapspot.domain.REFRESH_ID
import com.capstone.snapspot.domain.RegionRegistrar
import com.capstone.snapspot.domain.model.AddressDoc
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.KakaoDoc
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The glue between the database, the watch plan and the log, with a fake Kakao. */
@RunWith(AndroidJUnit4::class)
class WindowSyncTest {
    private lateinit var db: SnapSpotDatabase
    private lateinit var places: PlaceRepository
    private lateinit var coupons: CouponRepository
    private lateinit var events: EventLogRepository
    private lateinit var windows: WindowRepository
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private val here = Coords(37.5446, 127.0565)

    private class FakeKakao : KakaoLocalApi {
        var calls = 0
        var fail = false

        override suspend fun keyword(query: String, size: Int, x: Double?, y: Double?, radius: Int?, sort: String?, page: Int): List<KakaoDoc> {
            calls++
            if (fail) error("카카오 검색 실패 (500)")
            if (query != "스타벅스") return emptyList()
            return List(3) { i ->
                KakaoDoc(
                    id = "s$i", placeName = "스타벅스 ${i}호점", categoryName = "음식점 > 카페 > 커피전문점 > 스타벅스", categoryGroupCode = "",
                    addressName = "", roadAddressName = "서울", x = (127.0565 + (i + 1) * 0.001).toString(), y = "37.5446", placeUrl = "",
                )
            }
        }

        override suspend fun address(query: String, size: Int): List<AddressDoc> = emptyList()
    }

    private class FakeRegistrar : RegionRegistrar {
        val registered = mutableListOf<List<Region>>()
        var clears = 0
        var fail = false

        override suspend fun register(regions: List<Region>) {
            if (fail) error("위치가 꺼져 있어요")
            registered += regions
        }

        override suspend fun clear() {
            if (fail) error("위치가 꺼져 있어요")
            clears++
        }
    }

    private val kakao = FakeKakao()
    private val registrar = FakeRegistrar()
    private lateinit var sync: WindowSync

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SnapSpotDatabase::class.java).build()
        places = PlaceRepository(db.places())
        coupons = CouponRepository(db.coupons())
        events = EventLogRepository(db.events())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File.createTempFile("window", ".preferences_pb").also { it.delete() }
        windows = WindowRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        sync = WindowSync(places, coupons, windows, events, kakao, RoomBranchCache(db.branchCache()), registrar)
    }

    @After
    fun close() {
        db.close()
        scope.cancel()
        file.delete()
    }

    private fun place(id: String) = Place(id, id, "", "카페", "", null, 37.5450, 127.0570, 200, null, null, null, 0, null)

    private fun coupon(id: String = "c1", brand: String = "스타벅스") = Coupon(id, brand, "아메리카노", null, null, 0, null, null)

    @Test
    fun theFirstSyncSavesAPlanAndWritesAReplanLine() = runBlocking {
        places.save(place("p1"))
        val summary = sync.sync(here, now = 1_000)

        assertEquals(1, summary.active)
        val saved = windows.load()!!
        assertEquals(1_000L, saved.at)
        assertEquals(here, saved.here)
        assertEquals(mapOf("place" to 1), saved.byGroup)
        val log = events.getAll()
        assertEquals(listOf(EventKind.REPLAN), log.map { it.kind })
        assertEquals(1L, (log[0].detail!!["active"] as Number).toLong())
        assertEquals(true, log[0].detail!!["located"])
    }

    @Test
    fun anActiveCouponBringsItsBranchesIntoThePlanAndTheSearchIsCounted() = runBlocking {
        coupons.save(coupon())
        val summary = sync.sync(here, now = 2_000)

        assertEquals(3, summary.active)
        assertEquals(1, summary.requests)
        val saved = windows.load()!!
        assertEquals(setOf("b|스타벅스|s0", "b|스타벅스|s1", "b|스타벅스|s2"), saved.branches.keys)
        assertEquals("스타벅스 0호점", saved.branches["b|스타벅스|s0"]!!.name)
    }

    @Test
    fun theSecondSyncReusesTheCachedBranchSearch() = runBlocking {
        coupons.save(coupon())
        sync.sync(here, now = 1_000)
        val again = sync.sync(here, now = 2_000)
        assertEquals(0, again.requests)
        assertEquals(1, kakao.calls)
        assertEquals(2, events.getAll().size)
        assertEquals(0, windows.load()!!.requests)
    }

    @Test
    fun withoutAPositionOnlyPlacesAreWatchedAndNothingIsSearched() = runBlocking {
        places.save(place("p1"))
        coupons.save(coupon())
        sync.sync(null, now = 1_000)
        assertEquals(0, kakao.calls)
        val saved = windows.load()!!
        assertNull(saved.here)
        assertEquals(mapOf("place" to 1), saved.byGroup)
        assertEquals(false, events.getAll().single().detail!!["located"])
    }

    @Test
    fun aFailingSearchStillSavesAPlanForThePlaces() = runBlocking {
        places.save(place("p1"))
        coupons.save(coupon())
        kakao.fail = true
        val summary = sync.sync(here, now = 1_000)
        assertEquals(1, summary.active)
        assertTrue(windows.load()!!.branches.isEmpty())
    }

    @Test
    fun usedCouponsAndNothingSavedGiveAnEmptyPlan() = runBlocking {
        coupons.save(coupon().copy(usedAt = 5))
        val summary = sync.sync(here, now = 1_000)
        assertEquals(0, summary.active)
        assertEquals(0, kakao.calls)
        assertTrue(windows.load()!!.branches.isEmpty())
    }

    // --- registering the plan with the OS ---

    @Test
    fun thePlannedRegionsAreHandedToTheOs() = runBlocking {
        places.save(place("p1"))
        coupons.save(coupon())
        sync.sync(here, now = 1_000)

        val regions = registrar.registered.single()
        assertEquals(0, registrar.clears)
        // the refresh circle, the place and three branches
        assertEquals(setOf(REFRESH_ID, "p1", "b|스타벅스|s0", "b|스타벅스|s1", "b|스타벅스|s2"), regions.map { it.identifier }.toSet())
        val refresh = regions.first { it.identifier == REFRESH_ID }
        assertEquals(false, refresh.notifyOnEnter)
        assertEquals(true, refresh.notifyOnExit)
        assertEquals(1000, refresh.radius)
        val branch = regions.first { it.identifier == "b|스타벅스|s0" }
        assertEquals(true, branch.notifyOnEnter)
        assertEquals(200, branch.radius)
    }

    @Test
    fun anEmptyPlanClearsWhatWasRegisteredBefore() = runBlocking {
        sync.sync(null, now = 1_000)
        assertEquals(1, registrar.clears)
        assertTrue(registrar.registered.isEmpty())
    }

    @Test
    fun aDeletedPlaceIsLeftOutOfTheNextRegistration() = runBlocking {
        places.save(place("p1"))
        sync.sync(here, now = 1_000)
        places.remove("p1")
        sync.sync(here, now = 2_000)

        assertTrue("p1" in registrar.registered[0].map { it.identifier })
        assertEquals(listOf(REFRESH_ID), registrar.registered[1].map { it.identifier })
    }

    @Test
    fun aRefusedRegistrationStillLeavesTheSavedPlanAndTheLog() = runBlocking {
        places.save(place("p1"))
        registrar.fail = true
        val summary = sync.sync(here, now = 1_000)

        assertEquals(1, summary.active)
        assertEquals(1_000L, windows.load()!!.at)
        assertEquals(listOf(EventKind.REPLAN), events.getAll().map { it.kind })
    }
}
