package com.capstone.snapspot.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.ui.coupon.CouponDetailContent
import com.capstone.snapspot.ui.coupon.CouponSwipe
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The coupon screen's swipe to the other active coupons of the same brand. Dates are far out so "now" does not matter. */
@RunWith(AndroidJUnit4::class)
class CouponSwipeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(coupons: List<Coupon>, id: String) = rule.setContent {
        SnapSpotTheme {
            CouponSwipe(coupons, id) { c ->
                CouponDetailContent(c, null, HERE, onClose = {}, onDelete = {}, onUsed = {}, onToCoupons = {})
            }
        }
    }

    private fun mega(id: String, benefit: String, expiresAt: String) = coupon(id, "메가MGC커피", benefit, expiresAt)

    private val soon = mega("soon", "먼저 쿠폰", "2098-01-01")
    private val late = mega("late", "나중 쿠폰", "2099-12-31")

    @Test
    fun theScreenStartsAtTheCouponThatWasOpenedAndSwipesToTheNextOne() {
        show(listOf(late, soon), "soon")
        rule.waitForIdle()
        rule.onNodeWithText("먼저 쿠폰").assertExists()
        rule.onNodeWithText("나중 쿠폰").assertDoesNotExist()

        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("나중 쿠폰").assertExists()
        rule.onNodeWithText("먼저 쿠폰").assertDoesNotExist()
    }

    @Test
    fun swipingBackGoesToTheSoonerCoupon() {
        show(listOf(late, soon), "late")
        rule.waitForIdle()
        rule.onNodeWithText("나중 쿠폰").assertExists()

        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("먼저 쿠폰").assertExists()
    }

    @Test
    fun theDotsTellWhereYouAre() {
        show(listOf(late, soon), "soon")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("쿠폰 2개 중 1번째").assertExists()

        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("쿠폰 2개 중 2번째").assertExists()
    }

    @Test
    fun aBrandWithOneCouponHasNothingToSwipeTo() {
        show(listOf(soon, coupon("other", "스타벅스", "다른 브랜드 쿠폰", "2098-01-01")), "soon")
        rule.waitForIdle()
        rule.onNodeWithText("먼저 쿠폰").assertExists()
        rule.onAllNodesWithContentDescription("개 중", substring = true).assertCountEquals(0)

        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("먼저 쿠폰").assertExists()
        rule.onNodeWithText("다른 브랜드 쿠폰").assertDoesNotExist()
    }

    @Test
    fun aUsedCouponIsShownAlone() {
        val used = mega("used", "쓴 쿠폰", "2098-06-01").copy(usedAt = 1L)
        show(listOf(soon, used, late), "used")
        rule.waitForIdle()
        rule.onNodeWithText("쓴 쿠폰").assertExists()
        rule.onAllNodesWithContentDescription("개 중", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMissingCouponStillShowsTheWayBack() {
        show(listOf(soon, late), "gone")
        rule.waitForIdle()
        rule.onNodeWithText("쿠폰을 찾지 못했어요").assertExists()
    }
}
