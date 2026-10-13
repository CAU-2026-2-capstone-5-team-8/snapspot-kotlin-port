package com.capstone.snapspot.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.capstone.snapspot.data.notify.CHANNEL_ID
import com.capstone.snapspot.data.notify.Notifier
import com.capstone.snapspot.data.notify.alertUri
import com.capstone.snapspot.data.notify.createAlertChannel
import com.capstone.snapspot.domain.Alert
import com.capstone.snapspot.domain.AlertKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Alerts as real Android notifications. */
@RunWith(AndroidJUnit4::class)
class NotifierTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = Notifier(context)

    @Before
    fun clean() = manager.cancelAll()

    @After
    fun cleanUp() = manager.cancelAll()

    private fun coupon(id: String = "c_1") = Alert(AlertKind.COUPON, id, "🎟️ 스타벅스 성수점 근처예요", "스타벅스 아메리카노 · 12월 31일까지")

    private fun active() = manager.activeNotifications.filter { it.packageName == context.packageName }

    /** The system posts notifications asynchronously, so wait (a few seconds at most) for [count] of them to show. */
    private fun waitFor(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (active().size != count && System.currentTimeMillis() < deadline) Thread.sleep(100)
    }

    @Test
    fun theChannelIsHighImportanceWithTheNearbyName() {
        createAlertChannel(context)
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals("근처 저장 장소 알림", channel.name.toString())
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun anAlertBecomesANotificationWithItsTitleAndBody() {
        assertTrue(notifier.show(coupon()))
        waitFor(1)
        val n = active().single().notification
        assertEquals("🎟️ 스타벅스 성수점 근처예요", n.extras.getString("android.title"))
        assertEquals("스타벅스 아메리카노 · 12월 31일까지", n.extras.getCharSequence("android.text").toString())
        assertEquals(CHANNEL_ID, n.channelId)
        assertNotNull(n.contentIntent)
    }

    @Test
    fun alertingAgainForTheSameTargetReplacesTheOldNotification() {
        notifier.show(coupon())
        notifier.show(coupon().copy(body = "스타벅스 아메리카노 외 1장"))
        // The system posts notifications asynchronously, so wait for the second one to land.
        val deadline = System.currentTimeMillis() + 5_000
        fun text() = active().singleOrNull()?.notification?.extras?.getCharSequence("android.text")?.toString()
        while (text() != "스타벅스 아메리카노 외 1장" && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(1, active().size)
        assertEquals("스타벅스 아메리카노 외 1장", text())
    }

    @Test
    fun differentTargetsStackSeparately() {
        notifier.show(coupon("c_1"))
        notifier.show(Alert(AlertKind.PLACE, "p_1", "📍 저장해 둔 카페 근처예요", "어니언 성수"))
        waitFor(2)
        assertEquals(2, active().size)
    }

    @Test
    fun tappingOpensTheCouponOrThePlaceByLink() {
        assertEquals("snapspot://coupon/c_1", alertUri(coupon("c_1")).toString())
        assertEquals("snapspot://place/p_1", alertUri(Alert(AlertKind.PLACE, "p_1", "t", "b")).toString())
    }

    @Test
    fun anIdWithSpecialCharactersStaysOneSegmentOfTheLink() {
        val uri = alertUri(Alert(AlertKind.COUPON, "a/b c", "t", "b"))
        assertEquals("coupon", uri.host)
        assertEquals(listOf("a/b c"), uri.pathSegments)
    }
}
