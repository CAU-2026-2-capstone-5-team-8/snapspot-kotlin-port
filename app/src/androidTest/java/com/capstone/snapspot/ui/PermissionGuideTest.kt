package com.capstone.snapspot.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.domain.HowToAsk
import com.capstone.snapspot.domain.PermissionStep
import com.capstone.snapspot.ui.permissions.PermissionGuideContent
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionGuideTest {
    @get:Rule
    val rule = createComposeRule()

    private val asked = mutableListOf<Pair<PermissionStep, HowToAsk>>()
    private var closed = 0
    private var fixedLocation = 0
    private var openedBattery = 0
    private var improvedAccuracy = 0

    private fun show(
        perms: AlertPermissions,
        sdk: Int = 36,
        denied: Set<PermissionStep> = emptySet(),
        deviceLocationOn: Boolean = true,
        batteryRestricted: Boolean = false,
        accuracyLow: Boolean = false,
    ) = rule.setContent {
        SnapSpotTheme {
            PermissionGuideContent(
                perms = perms,
                sdk = sdk,
                denied = denied,
                deviceLocationOn = deviceLocationOn,
                batteryRestricted = batteryRestricted,
                accuracyLow = accuracyLow,
                onAsk = { step, how -> asked += step to how },
                onFixDeviceLocation = { fixedLocation++ },
                onImproveAccuracy = { improvedAccuracy++ },
                onOpenBatterySettings = { openedBattery++ },
                onClose = { closed++ },
            )
        }
    }

    @Test
    fun theFirstStepIsLocationAndAsksWithAPopUp() {
        show(AlertPermissions(notif = false, fg = false, bg = false))
        rule.onNodeWithText("위치 허용").assertExists()
        rule.onNodeWithText("지금 할 일").assertExists()
        rule.onNodeWithText("허용하기").performClick()
        assertEquals(listOf(PermissionStep.FOREGROUND_LOCATION to HowToAsk.DIALOG), asked)
    }

    @Test
    fun onlyTheCurrentStepHasAButton() {
        show(AlertPermissions(notif = false, fg = false, bg = false))
        rule.onAllNodesWithText("허용하기").assertCountEquals(1)
    }

    @Test
    fun alwaysLocationSendsTheUserToSettingsAndSaysWhereToTap() {
        show(AlertPermissions(notif = true, fg = true, bg = false))
        rule.onNodeWithText("위치 \"항상 허용\"").assertExists()
        rule.onNodeWithText("설정 → 권한 → 위치에서", substring = true).assertExists()
        rule.onNodeWithText("설정에서 허용하기").performClick()
        assertEquals(listOf(PermissionStep.BACKGROUND_LOCATION to HowToAsk.SETTINGS), asked)
    }

    @Test
    fun finishedStepsAreMarkedDoneAndHaveNoButton() {
        show(AlertPermissions(notif = true, fg = true, bg = false))
        rule.onAllNodesWithText("완료").assertCountEquals(2)
    }

    @Test
    fun aRefusedStepSaysSoAndGoesToSettings() {
        show(AlertPermissions(notif = true, fg = false, bg = false), denied = setOf(PermissionStep.FOREGROUND_LOCATION))
        rule.onNodeWithText("허용하지 않으셨어요", substring = true).assertExists()
        rule.onNodeWithText("설정에서 허용하기").performClick()
        assertEquals(listOf(PermissionStep.FOREGROUND_LOCATION to HowToAsk.SETTINGS), asked)
    }

    @Test
    fun beforeAndroid13NotificationsGoToSettings() {
        show(AlertPermissions(notif = false, fg = true, bg = true), sdk = 32)
        rule.onNodeWithText("설정에서 이 앱의 알림을 켜 주세요.").assertExists()
        rule.onNodeWithText("설정에서 허용하기").performClick()
        assertEquals(listOf(PermissionStep.NOTIFICATIONS to HowToAsk.SETTINGS), asked)
    }

    @Test
    fun beforeAndroid10ThereIsNoAlwaysLocationStep() {
        show(AlertPermissions(notif = true, fg = true, bg = true), sdk = 28)
        rule.onAllNodesWithText("위치 \"항상 허용\"").assertCountEquals(0)
    }

    @Test
    fun whenThePhonesLocationIsOffItIsTheNextStepAfterAllPermissions() {
        show(AlertPermissions(notif = true, fg = true, bg = true), deviceLocationOn = false)
        rule.onNodeWithText("휴대폰 위치 켜기").assertExists()
        rule.onNodeWithText("위치 켜기").performClick()
        assertEquals(1, fixedLocation)
        rule.onAllNodesWithText("준비가 끝났어요").assertCountEquals(0)
    }

    @Test
    fun lowLocationAccuracyIsOnlyARecommendationAndNeverBlocksAllSet() {
        show(AlertPermissions(notif = true, fg = true, bg = true), accuracyLow = true)
        rule.onNodeWithText("위치 정확도 높이기 (권장)").performScrollTo().assertExists()
        rule.onNodeWithText("준비가 끝났어요").assertExists()
        rule.onNodeWithText("정확도 켜기").performScrollTo().performClick()
        assertEquals(1, improvedAccuracy)
        assertEquals(0, fixedLocation)
    }

    @Test
    fun noAccuracyHintWhenThePhonesLocationIsOffBecauseThatComesFirst() {
        show(AlertPermissions(notif = true, fg = true, bg = true), deviceLocationOn = false, accuracyLow = true)
        rule.onAllNodesWithText("위치 정확도 높이기 (권장)").assertCountEquals(0)
    }

    @Test
    fun theBatteryHintIsOptionalAndOnlyAppearsWhenRestricted() {
        show(AlertPermissions(notif = true, fg = true, bg = true), batteryRestricted = true)
        rule.onNodeWithText("배터리 사용 제한 풀기 (권장)").performScrollTo().assertExists()
        rule.onNodeWithText("배터리 설정 열기").performScrollTo().performClick()
        assertEquals(1, openedBattery)
        // It never blocks "all set".
        rule.onNodeWithText("준비가 끝났어요").assertExists()
    }

    @Test
    fun noBatteryHintWhenNotRestricted() {
        show(AlertPermissions(notif = true, fg = true, bg = true), batteryRestricted = false)
        rule.onAllNodesWithText("배터리 사용 제한 풀기 (권장)").assertCountEquals(0)
    }

    @Test
    fun whenEverythingIsOnTheGuideSaysSoAndClosesWithTheButton() {
        show(AlertPermissions(notif = true, fg = true, bg = true))
        rule.onNodeWithText("준비가 끝났어요").assertExists()
        rule.onNodeWithText("닫기").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun laterLeavesTheGuideWhileSomethingIsMissing() {
        show(AlertPermissions(notif = false, fg = false, bg = false))
        rule.onNodeWithText("나중에").performScrollTo().performClick()
        assertEquals(1, closed)
    }
}
