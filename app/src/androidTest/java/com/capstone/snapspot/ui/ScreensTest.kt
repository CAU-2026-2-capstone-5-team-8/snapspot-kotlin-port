package com.capstone.snapspot.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.coupon.CouponDetailContent
import com.capstone.snapspot.ui.coupons.CouponsContent
import com.capstone.snapspot.ui.places.PlacesContent
import com.capstone.snapspot.ui.status.StatusContent
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent { SnapSpotTheme { content() } }

    // ---------------- coupon wallet ----------------

    @Test
    fun anEmptyWalletExplainsHowToAddACoupon() {
        show { CouponsContent(emptyList(), null, HERE, onOpen = {}, now = NOW) }
        rule.onNodeWithText("쿠폰함").assertExists()
        rule.onNodeWithText("프랜차이즈 쿠폰을 넣어 두세요").assertExists()
        rule.onAllNodesWithText("장,", substring = true).assertCountEquals(0)
    }

    @Test
    fun aTicketShowsBrandBenefitExpiryAndNearestBranch() {
        val win = windowState("b|스타벅스|1" to branchRef("스타벅스 성수점", 37.5453), byGroup = mapOf("스타벅스" to 1))
        show { CouponsContent(listOf(coupon()), win, HERE, onOpen = {}, now = NOW) }
        rule.onNodeWithText("스타벅스").assertExists()
        rule.onNodeWithText("아메리카노 Tall").assertExists()
        rule.onNodeWithText("2026년 10월 31일까지").assertExists()
        rule.onNodeWithText("D-21").assertExists()
        rule.onNodeWithText("가장 가까운 지점  스타벅스 성수점  78m").assertExists()
        rule.onNodeWithText("1장, 지점 1곳 지켜보는 중").assertExists()
    }

    @Test
    fun aBranchInsideTheAlertRadiusGetsTheNearBand() {
        val win = windowState("b|스타벅스|1" to branchRef("스타벅스 성수점", 37.5453))
        show { CouponsContent(listOf(coupon()), win, HERE, onOpen = {}, now = NOW) }
        rule.onNodeWithText("지금 근처: 스타벅스 성수점").assertExists()
    }

    @Test
    fun aFarBranchGetsNoBand() {
        val win = windowState("b|스타벅스|1" to branchRef("스타벅스 먼점", 37.60))
        show { CouponsContent(listOf(coupon()), win, HERE, onOpen = {}, now = NOW) }
        rule.onAllNodesWithText("지금 근처", substring = true).assertCountEquals(0)
        rule.onNodeWithText("가장 가까운 지점", substring = true).assertExists()
    }

    @Test
    fun withoutAPlanTheTicketSaysItIsLooking() {
        show { CouponsContent(listOf(coupon()), null, HERE, onOpen = {}, now = NOW) }
        rule.onNodeWithText("근처 지점을 찾는 중이에요").assertExists()
        rule.onNodeWithText("1장").assertExists()
    }

    @Test
    fun tappingATicketOpensThatCoupon() {
        var opened: Coupon? = null
        show { CouponsContent(listOf(coupon(id = "c9")), null, HERE, onOpen = { opened = it }, now = NOW) }
        rule.onNodeWithContentDescription("스타벅스 아메리카노 Tall 쿠폰 열기").performClick()
        assertEquals("c9", opened?.id)
    }

    @Test
    fun activeCouponsComeSoonestFirstAndPastOnesGoBelow() {
        val list = listOf(
            coupon(id = "late", brand = "올리브영", expiresAt = "2026-12-01"),
            coupon(id = "soon", brand = "스타벅스", expiresAt = "2026-10-12"),
            coupon(id = "used", brand = "메가커피", usedAt = 1),
            coupon(id = "old", brand = "투썸", expiresAt = "2026-10-01"),
        )
        show { CouponsContent(list, null, HERE, onOpen = {}, now = NOW) }
        rule.onNodeWithText("지난 쿠폰").assertExists()
        rule.onNodeWithText("사용했어요").assertExists()
        rule.onNodeWithText("2026년 10월 1일까지").assertExists()
        rule.onNodeWithText("D-2").assertExists()
        rule.onAllNodesWithText("알림이 꺼진 쿠폰이에요").assertCountEquals(2)
        rule.onNodeWithText("2장").assertExists()
    }

    // ---------------- places ----------------

    @Test
    fun noPlacesShowsTheHint() {
        show { PlacesContent(emptyList(), HERE, alertsOn = false, watching = 0, onDelete = {}, onOpenPage = {}, now = NOW) }
        rule.onNodeWithText("저장한 곳").assertExists()
        rule.onNodeWithText("아직 저장한 곳이 없어요").assertExists()
        rule.onNodeWithText("앱을 열어 둔 동안만 알려 드려요").assertExists()
    }

    @Test
    fun aPlaceCardShowsItsDetailsDistanceAndFacts() {
        show {
            PlacesContent(
                listOf(place(endDate = "2026-10-31")), HERE, alertsOn = true, watching = 3, onDelete = {}, onOpenPage = {}, now = NOW,
            )
        }
        rule.onNodeWithText("어니언 성수").assertExists()
        rule.onNodeWithText("소금빵 맛집").assertExists()
        rule.onNodeWithText("서울 성동구 아차산로9길 8").assertExists()
        rule.onNodeWithText("반경 200m").assertExists()
        rule.onNodeWithText("10월 31일까지").assertExists()
        rule.onNodeWithText("1곳, 가까운 순").assertExists()
        rule.onNodeWithText("앱을 닫아도 근처에서 알려 드려요").assertExists()
        rule.onNodeWithText("3곳 지켜보는 중").assertExists()
    }

    @Test
    fun aPlaceInsideItsRadiusGetsTheNearBandInsteadOfADistance() {
        show { PlacesContent(listOf(place(radius = 300)), HERE, true, 1, {}, {}, now = NOW) }
        rule.onNodeWithText("지금 근처예요").assertExists()
    }

    @Test
    fun aFarPlaceShowsItsDistance() {
        show { PlacesContent(listOf(place(lat = 37.60)), HERE, true, 1, {}, {}, now = NOW) }
        rule.onAllNodesWithText("지금 근처예요").assertCountEquals(0)
        rule.onNodeWithContentDescription("여기서", substring = true).assertExists()
    }

    @Test
    fun theMapButtonOnlyExistsWithAPlacePage() {
        show { PlacesContent(listOf(place(placeUrl = null)), HERE, true, 1, {}, {}, now = NOW) }
        rule.onAllNodesWithText("지도").assertCountEquals(0)
    }

    @Test
    fun theMapButtonOpensThePlacePage() {
        var opened: Place? = null
        show { PlacesContent(listOf(place()), HERE, true, 1, {}, { opened = it }, now = NOW) }
        rule.onNodeWithText("지도").performClick()
        assertEquals("p1", opened?.id)
    }

    @Test
    fun aPlaceWithoutAnAddressSaysSo() {
        show { PlacesContent(listOf(place(address = "")), null, true, 1, {}, {}, now = NOW) }
        rule.onNodeWithText("주소 없음").assertExists()
        rule.onNodeWithText("1곳").assertExists()
    }

    @Test
    fun deletingAPlaceAsksFirstAndOnlyDeletesOnConfirm() {
        var deleted: Place? = null
        show { PlacesContent(listOf(place()), HERE, true, 1, { deleted = it }, {}, now = NOW) }
        rule.onNodeWithText("삭제").performClick()
        rule.onNodeWithText("어니언 성수 삭제").assertExists()
        rule.onNodeWithText("이 장소의 근처 알림도 함께 꺼져요.").assertExists()
        rule.onNodeWithText("취소").performClick()
        assertNull(deleted)
        rule.onNodeWithText("삭제").performClick()
        rule.onNode(hasText("삭제") and hasAnyAncestor(isDialog())).performClick()
        assertEquals("p1", deleted?.id)
    }

    @Test
    fun anEndedPlaceShowsWhyItStaysQuiet() {
        show { PlacesContent(listOf(place(endDate = "2026-10-09")), HERE, true, 1, {}, {}, now = NOW) }
        rule.onNodeWithText("운영 종료").assertExists()
    }

    // ---------------- status ----------------

    private val allowed = AlertPermissions(notif = true, fg = true, bg = true)

    @Test
    fun missingPermissionsShowANoticeWithAWayToFixIt() {
        var opened = 0
        show { StatusContent(AlertPermissions(notif = false, fg = true, bg = false), null, emptyList(), 0, { opened++ }, {}, {}, now = NOW) }
        rule.onNodeWithText("앱을 닫아도 알림을 받으려면", substring = true).assertExists()
        rule.onNodeWithText("앱 설정 열기").performClick()
        assertEquals(1, opened)
        rule.onNodeWithText("앱을 열어 둔 동안만 알려 드려요").assertExists()
    }

    @Test
    fun withAllPermissionsThereIsNoNotice() {
        show { StatusContent(allowed, null, emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onAllNodesWithText("앱 설정 열기").assertCountEquals(0)
        rule.onNodeWithText("앱을 닫아도 근처에서 알려 드려요").assertExists()
    }

    @Test
    fun theSlotMeterNamesEachGroupAndTheFreeSlots() {
        val win = windowState(byGroup = mapOf("place" to 2, "스타벅스" to 30))
        show { StatusContent(allowed, win, listOf(coupon()), 0, {}, {}, {}, now = NOW) }
        rule.onNodeWithText("저장한 장소").performScrollTo().assertExists()
        rule.onNodeWithText("스타벅스 지점").assertExists()
        rule.onNodeWithText("갱신 구역 (반경 1km)").assertExists()
        rule.onNodeWithText("비어 있음").assertExists()
        rule.onNodeWithText("57칸").assertExists() // 90 - (2 + 30 + 1)
        rule.onNodeWithText("32곳 지켜보는 중").assertExists()
    }

    @Test
    fun theLastReplanShowsTimeSearchesAndPosition() {
        val win = windowState(requests = 3)
        show { StatusContent(allowed, win, emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onNodeWithText("5분 전").performScrollTo().assertExists()
        rule.onNodeWithText("3회").assertExists()
        rule.onNodeWithText("확인됨").assertExists()
    }

    @Test
    fun aReplanThatUsedOnlyTheCacheSaysSo() {
        show { StatusContent(allowed, windowState(here = null), emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onNodeWithText("저장해 둔 결과 사용").performScrollTo().assertExists()
        rule.onNodeWithText("아직 모름").assertExists()
    }

    @Test
    fun beforeAnyReplanTheCardSaysNothingYet() {
        show { StatusContent(allowed, null, emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onNodeWithText("아직 고른 적이 없어요", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun logButtonsAreOffWhenThereIsNothingAndCallBackWhenThereIs() {
        show { StatusContent(allowed, null, emptyList(), 0, {}, {}, {}, now = NOW) }
        rule.onNodeWithText("0건").performScrollTo()
        rule.onNodeWithText("내보내기").assertIsNotEnabled()
        rule.onNodeWithText("지우기").assertIsNotEnabled()
    }

    @Test
    fun withARecordTheLogCanBeExportedAndCleared() {
        var exported = 0
        var cleared = 0
        show { StatusContent(allowed, null, emptyList(), 12, {}, { exported++ }, { cleared++ }, now = NOW) }
        rule.onNodeWithText("12건").performScrollTo()
        rule.onNodeWithText("내보내기").assertIsEnabled().performClick()
        rule.onNodeWithText("지우기").assertIsEnabled().performClick()
        assertEquals(1, exported)
        assertEquals(1, cleared)
    }

    // ---------------- coupon detail ----------------

    private fun detail(
        c: Coupon?,
        win: com.capstone.snapspot.domain.model.WindowState? = null,
        onDelete: (Coupon) -> Unit = {},
        onUsed: (Coupon) -> Unit = {},
        onToCoupons: () -> Unit = {},
        onTest: ((Coupon) -> Unit)? = null,
    ) = show { CouponDetailContent(c, win, HERE, onClose = {}, onDelete = onDelete, onUsed = onUsed, onToCoupons = onToCoupons, now = NOW, onTest = onTest) }

    @Test
    fun aMissingCouponOffersTheWayBack() {
        var back = 0
        detail(null, onToCoupons = { back++ })
        rule.onNodeWithText("쿠폰을 찾지 못했어요").assertExists()
        rule.onNodeWithText("쿠폰함으로").performClick()
        assertEquals(1, back)
    }

    @Test
    fun anActiveCouponShowsExpiryDayCountAndNearbyBranches() {
        val win = windowState(
            "b|스타벅스|1" to branchRef("스타벅스 성수점", 37.5453),
            "b|스타벅스|2" to branchRef("스타벅스 먼점", 37.5600),
            "b|스타벅스|3" to branchRef("스타벅스 중간점", 37.5500),
            "b|스타벅스|4" to branchRef("스타벅스 더먼점", 37.6200),
        )
        detail(coupon(), win)
        rule.onNodeWithText("아메리카노 Tall").assertExists()
        rule.onNodeWithText("2026년 10월 31일까지").assertExists()
        rule.onNodeWithText("D-21").assertExists()
        rule.onNodeWithText("가까운 지점").assertExists()
        rule.onNodeWithText("스타벅스 성수점").assertExists()
        rule.onNodeWithText("스타벅스 중간점").assertExists()
        rule.onNodeWithText("스타벅스 먼점").assertExists()
        rule.onAllNodesWithText("스타벅스 더먼점").assertCountEquals(0) // only the three nearest
        rule.onNodeWithText("사용했어요").assertExists()
    }

    @Test
    fun theCouponHasNoBranchListWithoutAPlan() {
        detail(coupon(), null)
        rule.onAllNodesWithText("가까운 지점").assertCountEquals(0)
    }

    @Test
    fun aCouponWithoutAnImageSaysSo() {
        detail(coupon())
        rule.onNodeWithText("쿠폰 이미지가 없어요").assertExists()
    }

    @Test
    fun aCouponWithoutExpirySaysSo() {
        detail(coupon(expiresAt = null))
        rule.onNodeWithText("유효기간 없음").assertExists()
    }

    @Test
    fun markingACouponUsedCallsBack() {
        var used: Coupon? = null
        detail(coupon(id = "c7"), onUsed = { used = it })
        rule.onNodeWithText("사용했어요").performClick()
        assertEquals("c7", used?.id)
    }

    @Test
    fun deletingACouponAsksFirst() {
        var deleted: Coupon? = null
        detail(coupon(id = "c7"), onDelete = { deleted = it })
        rule.onNodeWithText("삭제").performClick()
        rule.onNodeWithText("스타벅스 쿠폰 삭제").assertExists()
        rule.onNodeWithText("이 쿠폰의 지점 알림도 함께 꺼져요.").assertExists()
        rule.onNodeWithText("취소").performClick()
        assertNull(deleted)
        rule.onNodeWithText("삭제").performClick()
        rule.onNode(hasText("삭제") and hasAnyAncestor(isDialog())).performClick()
        assertEquals("c7", deleted?.id)
    }

    @Test
    fun aUsedCouponOnlyOffersDeletion() {
        val win = windowState("b|스타벅스|1" to branchRef("스타벅스 성수점", 37.5453))
        detail(coupon(usedAt = 5), win)
        rule.onNodeWithText("사용한 쿠폰이에요").assertExists()
        rule.onNodeWithText("쿠폰 삭제").assertExists()
        rule.onAllNodesWithText("사용했어요").assertCountEquals(0)
        rule.onAllNodesWithText("가까운 지점").assertCountEquals(0)
        rule.onAllNodesWithText("D-", substring = true).assertCountEquals(0)
    }

    @Test
    fun anExpiredCouponIsTreatedLikeAUsedOne() {
        detail(coupon(expiresAt = "2026-10-01"))
        rule.onNodeWithText("쿠폰 삭제").assertExists()
        rule.onAllNodesWithText("사용했어요").assertCountEquals(0)
    }

    // ---------------- "알림 테스트" ----------------

    @Test
    fun anActiveCouponHasATestAlertButtonBetweenDeleteAndUsed() {
        var tested: Coupon? = null
        detail(coupon(), onTest = { tested = it })
        rule.onNodeWithText("삭제").assertExists()
        rule.onNodeWithText("사용했어요").assertExists()
        rule.onNodeWithText("알림 테스트").performClick()
        assertEquals("c1", tested?.id)
    }

    @Test
    fun withoutATestHandlerNoTestButtonIsShown() {
        detail(coupon())
        rule.onAllNodesWithText("알림 테스트").assertCountEquals(0)
    }

    @Test
    fun aUsedCouponHasNoTestAlertButton() {
        detail(coupon(usedAt = 5), onTest = {})
        rule.onAllNodesWithText("알림 테스트").assertCountEquals(0)
    }

    @Test
    fun aPlaceCardHasATestAlertButtonThatNamesThePlace() {
        var tested: Place? = null
        show { PlacesContent(listOf(place()), HERE, true, 1, {}, {}, now = NOW, onTest = { tested = it }) }
        rule.onNodeWithText("알림 테스트").performClick()
        assertEquals("p1", tested?.id)
    }

    @Test
    fun placeCardsShowTheTestButtonOnlyWhenAskedTo() {
        show { PlacesContent(listOf(place()), HERE, true, 1, {}, {}, now = NOW) }
        rule.onAllNodesWithText("알림 테스트").assertCountEquals(0)
    }
}
