package com.capstone.snapspot.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.data.db.SnapSpotDatabase
import com.capstone.snapspot.data.notify.AlertCenter
import com.capstone.snapspot.data.repo.CouponRepository
import com.capstone.snapspot.data.repo.EventLogRepository
import com.capstone.snapspot.data.repo.PlaceRepository
import com.capstone.snapspot.data.store.WindowRepository
import com.capstone.snapspot.domain.Alert
import com.capstone.snapspot.domain.AlertKind
import com.capstone.snapspot.domain.AlertSink
import com.capstone.snapspot.domain.BRAND_COOLDOWN_MS
import com.capstone.snapspot.domain.COOLDOWN_MS
import com.capstone.snapspot.domain.EventKind
import com.capstone.snapspot.domain.brandKey
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Whether an alert goes out, what it says, and what is written down afterwards, with a recording stand-in for the notification. */
@RunWith(AndroidJUnit4::class)
class AlertCenterTest {
    private class RecordingSink : AlertSink {
        // Two alerts can arrive on different threads at once.
        val shown: MutableList<Alert> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var canShow = true

        override fun show(alert: Alert): Boolean {
            if (!canShow) return false
            shown += alert
            return true
        }
    }

    private lateinit var db: SnapSpotDatabase
    private lateinit var places: PlaceRepository
    private lateinit var coupons: CouponRepository
    private lateinit var events: EventLogRepository
    private lateinit var windows: WindowRepository
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private val sink = RecordingSink()
    private lateinit var center: AlertCenter
    private val t0 = 1_000_000_000_000L

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SnapSpotDatabase::class.java).build()
        places = PlaceRepository(db.places())
        coupons = CouponRepository(db.coupons())
        events = EventLogRepository(db.events())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File.createTempFile("alerts", ".preferences_pb").also { it.delete() }
        windows = WindowRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        center = AlertCenter(places, coupons, windows, events, sink)
    }

    @After
    fun close() {
        db.close()
        scope.cancel()
        file.delete()
    }

    private fun place(id: String = "p1", endDate: String? = null, last: Long? = null) =
        Place(id, "어니언 성수", "서울", "카페", "소금빵", endDate, 37.5450, 127.0570, 200, null, null, null, 0, last)

    private fun coupon(id: String = "c1", brand: String = "스타벅스", expiresAt: String? = null, usedAt: Long? = null, last: Long? = null) =
        Coupon(id, brand, "아메리카노", expiresAt, null, 0, usedAt, last)

    private suspend fun log() = events.getAll()

    // --- places ---

    @Test
    fun aPlaceAlertIsShownMarkedAndLogged() = runBlocking {
        places.save(place())
        assertTrue(center.notifyPlace("p1", now = t0))

        val a = sink.shown.single()
        assertEquals(AlertKind.PLACE, a.kind)
        assertEquals("📍 저장해 둔 카페 근처예요", a.title)
        assertEquals("어니언 성수 · 소금빵", a.body)
        assertEquals(t0, places.get("p1")!!.lastNotifiedAt)
        val e = log().single()
        assertEquals(EventKind.NOTIFY_SHOWN, e.kind)
        assertEquals("p1", e.id)
        assertEquals("place", e.detail!!["type"])
        assertEquals(false, e.detail!!["forced"])
    }

    @Test
    fun theSamePlaceDoesNotAlertAgainWithinSixHours() = runBlocking {
        places.save(place())
        assertTrue(center.notifyPlace("p1", now = t0))
        assertFalse(center.notifyPlace("p1", now = t0 + COOLDOWN_MS - 1))

        assertEquals(1, sink.shown.size)
        val suppressed = log().last()
        assertEquals(EventKind.NOTIFY_SUPPRESSED, suppressed.kind)
        assertEquals("최근에 알림", suppressed.detail!!["reason"])
        assertEquals(t0, places.get("p1")!!.lastNotifiedAt) // the held-back alert did not restart the cooldown
    }

    @Test
    fun afterTheCooldownThePlaceAlertsAgain() = runBlocking {
        places.save(place())
        center.notifyPlace("p1", now = t0)
        assertTrue(center.notifyPlace("p1", now = t0 + COOLDOWN_MS))
        assertEquals(2, sink.shown.size)
    }

    @Test
    fun anEndedPlaceStaysQuiet() = runBlocking {
        places.save(place(endDate = "2000-01-01"))
        assertFalse(center.notifyPlace("p1", now = t0))
        assertTrue(sink.shown.isEmpty())
        assertEquals("운영 종료", log().single().detail!!["reason"])
    }

    @Test
    fun forcingSkipsTheCooldownAndSaysSoInTheLog() = runBlocking {
        places.save(place(last = t0 - 1_000))
        assertTrue(center.notifyPlace("p1", force = true, now = t0))
        assertEquals(true, log().single().detail!!["forced"])
    }

    @Test
    fun aPlaceThatWasDeletedMeanwhileIsIgnored() = runBlocking {
        assertFalse(center.notifyPlace("gone", now = t0))
        assertTrue(sink.shown.isEmpty())
        assertTrue(log().isEmpty())
    }

    @Test
    fun twoEntersAtTheSameTimeGiveOneAlert() = runBlocking {
        places.save(place())
        val results = listOf(
            async(Dispatchers.Default) { center.notifyPlace("p1", now = t0) },
            async(Dispatchers.Default) { center.notifyPlace("p1", now = t0 + 5) },
        ).awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, sink.shown.size)
    }

    @Test
    fun whenTheAlertCannotBeShownNothingCoolsDownAndTheLogSaysWhy() = runBlocking {
        places.save(place())
        sink.canShow = false
        assertFalse(center.notifyPlace("p1", now = t0))

        assertNull(places.get("p1")!!.lastNotifiedAt)
        val e = log().single()
        assertEquals(EventKind.NOTIFY_SUPPRESSED, e.kind)
        assertEquals("알림 권한 없음", e.detail!!["reason"])

        sink.canShow = true // once allowed, the same place can still alert right away
        assertTrue(center.notifyPlace("p1", now = t0 + 10))
    }

    // --- branches ---

    private val key = brandKey("스타벅스")
    private val region = "b|$key|77"

    private suspend fun planWithBranch() = windows.save(
        WindowState(t0, null, 90, emptyMap(), mapOf(region to BranchRef("스타벅스 성수점", key, 37.5448, 127.0565)), 0),
    )

    @Test
    fun aBranchAlertNamesTheBranchAndTheCoupon() = runBlocking {
        coupons.save(coupon(expiresAt = "2099-12-31"))
        planWithBranch()
        assertTrue(center.notifyBranch(region, now = t0))

        val a = sink.shown.single()
        assertEquals(AlertKind.COUPON, a.kind)
        assertEquals("c1", a.targetId)
        assertEquals("🎟️ 스타벅스 성수점 근처예요", a.title)
        assertEquals("스타벅스 아메리카노 · 12월 31일까지", a.body)
        val e = log().single()
        assertEquals(region, e.id)
        assertEquals("branch", e.detail!!["type"])
        assertEquals("스타벅스 성수점", e.detail!!["branch"])
    }

    @Test
    fun withoutASavedPlanTheBranchAlertNamesTheBrand() = runBlocking {
        coupons.save(coupon())
        assertTrue(center.notifyBranch(region, now = t0))
        assertEquals("🎟️ 스타벅스 근처예요", sink.shown.single().title)
        assertNull(log().single().detail!!["branch"])
    }

    @Test
    fun everyCouponOfTheBrandIsMarkedTogetherSoTheBrandCoolsDown() = runBlocking {
        coupons.save(coupon("c1"))
        coupons.save(coupon("c2"))
        assertTrue(center.notifyBranch(region, now = t0))
        assertEquals("스타벅스 아메리카노 외 1장", sink.shown.single().body)
        assertEquals(setOf<Long?>(t0), coupons.getAll().map { it.lastNotifiedAt }.toSet())

        assertFalse(center.notifyBranch(region, now = t0 + BRAND_COOLDOWN_MS - 1))
        assertEquals("브랜드 쿨다운", log().last().detail!!["reason"])
        assertTrue(center.notifyBranch(region, now = t0 + BRAND_COOLDOWN_MS))
    }

    @Test
    fun aBrandWithNoUsableCouponIsHeldBackEvenWhenForced() = runBlocking {
        coupons.save(coupon(usedAt = 5))
        assertFalse(center.notifyBranch(region, force = true, now = t0))
        assertTrue(sink.shown.isEmpty())
        assertEquals("사용할 쿠폰 없음", log().single().detail!!["reason"])
    }

    @Test
    fun forcingABranchAlertSkipsTheBrandCooldown() = runBlocking {
        coupons.save(coupon(last = t0 - 1_000))
        assertTrue(center.notifyBranch(region, force = true, now = t0))
        assertEquals(true, log().single().detail!!["forced"])
    }

    @Test
    fun aBranchAlertThatCannotBeShownDoesNotUseUpTheCoupon() = runBlocking {
        coupons.save(coupon())
        sink.canShow = false
        assertFalse(center.notifyBranch(region, now = t0))
        assertNull(coupons.get("c1")!!.lastNotifiedAt)
        assertNotNull(log().single().detail!!["reason"])
    }
}
