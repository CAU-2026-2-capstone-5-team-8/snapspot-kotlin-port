package com.capstone.snapspot.ui

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.ui.coupon.CouponDetailContent
import com.capstone.snapspot.ui.map.MapContent
import com.capstone.snapspot.ui.map.MapSupport
import com.capstone.snapspot.ui.nav.Route
import com.capstone.snapspot.ui.nav.TabBar
import com.capstone.snapspot.ui.permissions.PermissionGuideContent
import com.capstone.snapspot.ui.status.StatusContent
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What TalkBack and a finger need: big enough targets, headings, and a selected tab that says so. */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent { SnapSpotTheme { content() } }

    private val allowed = AlertPermissions(notif = true, fg = true, bg = true)

    @Test
    fun theCouponCloseButtonIsAtLeast48dpAndStillCloses() {
        var closed = 0
        show { CouponDetailContent(coupon(), null, HERE, onClose = { closed++ }, onDelete = {}, onUsed = {}, onToCoupons = {}, now = NOW) }
        rule.onNodeWithContentDescription("닫기").assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun theCouponDetailBrandIsAHeading() {
        show { CouponDetailContent(coupon(brand = "스타벅스"), null, HERE, onClose = {}, onDelete = {}, onUsed = {}, onToCoupons = {}, now = NOW) }
        rule.onNode(isHeading() and hasText("스타벅스")).assertExists()
    }

    @Test
    fun theStatusTitleIsAHeading() {
        show { StatusContent(allowed, null, emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onNode(isHeading() and hasText("알림 현황")).assertExists()
    }

    @Test
    fun theGuideTitleIsAHeading() {
        show {
            PermissionGuideContent(
                perms = allowed, sdk = 36, denied = emptySet(), deviceLocationOn = true, batteryRestricted = false, accuracyLow = false,
                onAsk = { _, _ -> }, onFixDeviceLocation = {}, onImproveAccuracy = {}, onOpenBatterySettings = {}, onClose = {},
            )
        }
        rule.onNode(isHeading() and hasText("알림 받을 준비")).assertExists()
    }

    @Test
    fun theSelectedTabSaysSoAndTheOthersDoNot() {
        show { TabBar(current = Route.COUPONS, onSelect = {}, onAdd = {}) }
        rule.onNodeWithText("쿠폰함").assertIsSelected()
        rule.onNodeWithText("지도").assertIsNotSelected()
        rule.onNodeWithText("장소").assertIsNotSelected()
        rule.onNodeWithText("현황").assertIsNotSelected()
    }

    @Test
    fun theSetupPillOnTheMapHasAtLeast48dpOfTouchHeightAndOpensTheGuide() {
        var opened = 0
        show {
            MapContent(
                places = emptyList(), here = HERE, win = windowState(), alertsOn = false, watching = 0,
                justSavedId = null, onJustSavedShown = {}, mapSupport = MapSupport.NO_KEY,
                onSetupAlerts = { opened++ },
            )
        }
        rule.onNodeWithText("알림 설정 필요").assertTouchHeightIsEqualTo(48.dp).performClick()
        assertEquals(1, opened)
    }
}
