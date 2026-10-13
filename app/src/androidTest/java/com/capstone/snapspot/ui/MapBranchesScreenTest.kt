package com.capstone.snapspot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.brandKey
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.map.MapContent
import com.capstone.snapspot.ui.map.MapParams
import com.capstone.snapspot.ui.map.MapSupport
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The map tab's badges, ended places, following and "이 지역에서 다시 탐색", with a stand-in for the Kakao map. The test
 * acts as the map: it reports camera moves (by hand or by the app) and badge taps through [MapParams].
 */
@RunWith(AndroidJUnit4::class)
class MapBranchesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var lastParams: MapParams? = null
    private val stubMap: @androidx.compose.runtime.Composable (MapParams, Modifier) -> Unit = { p, _ ->
        lastParams = p
        Column { Text("map:following=${p.following}") }
    }

    private val starbucks = brandKey("스타벅스")
    private val branch = mapOf("b|$starbucks|1" to BranchRef("스타벅스 성수점", starbucks, 37.54, 127.05))

    private val coupons = mutableStateOf<List<Coupon>>(emptyList())
    private val places = mutableStateOf<List<Place>>(emptyList())
    private val opened = mutableListOf<String>()
    private val searched = mutableListOf<Coords>()
    private val heres = mutableListOf<Pair<Coords, Boolean>>()

    private fun active(id: String, benefit: String = "아메리카노", expiresAt: String? = null) =
        coupon(id = id, benefit = benefit, expiresAt = expiresAt)

    private fun show(here: Coords? = HERE) = rule.setContent {
        SnapSpotTheme {
            MapContent(
                places = places.value,
                here = here,
                win = windowState(),
                alertsOn = true,
                watching = 1,
                justSavedId = null,
                onJustSavedShown = {},
                mapSupport = MapSupport.READY,
                map = stubMap,
                coupons = coupons.value,
                mapBranches = branch,
                onHere = { at, following -> heres += at to following },
                onSearchHere = { searched += it },
                onOpenCoupon = { opened += it },
            )
        }
    }

    private fun userMovesTheMap(lat: Double = 37.60, zoom: Int = 15) =
        rule.runOnUiThread { lastParams!!.onCamera(lat, 127.0, zoom, true) }

    private fun appMovesTheMap(lat: Double = 37.5446, zoom: Int = 15) =
        rule.runOnUiThread { lastParams!!.onCamera(lat, 127.0565, zoom, false) }

    // --- badges follow the coupons ------------------------------------------------------------------------------

    @Test
    fun aBrandWithAnActiveCouponHasItsBadgesOnTheMap() {
        coupons.value = listOf(active("c1"))
        show()
        rule.waitForIdle()
        assertEquals(1, lastParams!!.branches!!.size)
    }

    @Test
    fun whenTheLastCouponOfABrandIsGoneItsBadgeLeavesTheMapAtOnce() {
        coupons.value = listOf(active("c1"))
        show()
        rule.waitForIdle()
        assertEquals(1, lastParams!!.branches!!.size)

        // used up
        rule.runOnUiThread { coupons.value = listOf(coupon(id = "c1", usedAt = 1L)) }
        rule.waitForIdle()
        assertTrue(lastParams!!.branches!!.isEmpty())

        // a new coupon brings it back, deleting it takes it away again
        rule.runOnUiThread { coupons.value = listOf(active("c2")) }
        rule.waitForIdle()
        assertEquals(1, lastParams!!.branches!!.size)
        rule.runOnUiThread { coupons.value = emptyList() }
        rule.waitForIdle()
        assertTrue(lastParams!!.branches!!.isEmpty())
    }

    @Test
    fun anExpiredCouponDoesNotKeepTheBadge() {
        coupons.value = listOf(active("c1", expiresAt = "2020-01-01"))
        show()
        rule.waitForIdle()
        assertTrue(lastParams!!.branches!!.isEmpty())
    }

    // --- tapping a badge ----------------------------------------------------------------------------------------

    @Test
    fun aBadgeWithOneCouponOpensThatCoupon() {
        coupons.value = listOf(active("c1"))
        show()
        rule.waitForIdle()
        rule.runOnUiThread { lastParams!!.onBranch(starbucks) }
        rule.waitForIdle()
        assertEquals(listOf("c1"), opened)
    }

    @Test
    fun aBadgeWithSeveralCouponsListsThemSoonestExpiringFirst() {
        coupons.value = listOf(
            active("late", benefit = "나중 쿠폰", expiresAt = "2099-12-31"),
            active("soon", benefit = "먼저 쿠폰", expiresAt = "2098-01-01"),
        )
        show()
        rule.waitForIdle()
        rule.runOnUiThread { lastParams!!.onBranch(starbucks) }
        rule.waitForIdle()
        assertTrue(opened.isEmpty())
        val soon = rule.onNodeWithText("먼저 쿠폰").fetchSemanticsNode().positionInRoot.y
        val late = rule.onNodeWithText("나중 쿠폰").fetchSemanticsNode().positionInRoot.y
        assertTrue(soon < late)

        rule.onNodeWithText("먼저 쿠폰").performClick()
        rule.waitForIdle()
        assertEquals(listOf("soon"), opened)
        rule.onNodeWithText("나중 쿠폰").assertDoesNotExist()
    }

    // --- places that have ended ----------------------------------------------------------------------------------

    @Test
    fun anEndedPlaceIsNotOnTheMapNorInItsRail() {
        places.value = listOf(place(id = "open", name = "열린 곳"), place(id = "old", name = "끝난 곳", endDate = "2020-01-01"))
        show()
        rule.waitForIdle()
        assertEquals(listOf("open"), lastParams!!.places.map { it.id })
        rule.onNodeWithContentDescription("열린 곳, 지도에서 보기").assertExists()
        rule.onNodeWithContentDescription("끝난 곳, 지도에서 보기").assertDoesNotExist()
    }

    // --- following and the lookup button -------------------------------------------------------------------------

    @Test
    fun theMapFollowsAtFirstAndShowsNoButton() {
        show()
        rule.waitForIdle()
        assertTrue(lastParams!!.following)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
        rule.onNodeWithText("내 위치로").assertDoesNotExist()
    }

    @Test
    fun aMoveTheAppMakesItselfNeverShowsTheLookupButton() {
        show()
        repeat(3) { appMovesTheMap() }
        rule.waitForIdle()
        assertTrue(lastParams!!.following)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
    }

    @Test
    fun movingTheMapByHandStopsFollowingAndShowsTheButtonAndWayBack() {
        show()
        userMovesTheMap()
        rule.waitForIdle()
        assertFalse(lastParams!!.following)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertExists()
        rule.onNodeWithText("내 위치로").assertExists()
    }

    @Test
    fun pressingTheLookupButtonSearchesWhereTheMapIsAndHidesTheButton() {
        show()
        userMovesTheMap(lat = 37.60)
        rule.waitForIdle()
        rule.onNodeWithText("이 지역에서 다시 탐색").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Coords(37.60, 127.0)), searched)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
        assertFalse(lastParams!!.following)
    }

    @Test
    fun zoomedOutTooFarAsksForACloserViewInsteadOfTheButton() {
        show()
        userMovesTheMap(zoom = 9)
        rule.waitForIdle()
        rule.onNodeWithText("확대해 주세요").assertExists()
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
    }

    @Test
    fun backToMyPositionFollowsAgainAndHidesTheLookupButton() {
        show()
        userMovesTheMap()
        rule.waitForIdle()
        rule.onNodeWithText("내 위치로").performClick()
        rule.waitForIdle()
        assertTrue(lastParams!!.following)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
        rule.onNodeWithText("내 위치로").assertDoesNotExist()
        rule.onNodeWithText("map:following=true").assertExists()
    }

    @Test
    fun focusingAPlaceStopsFollowingWithoutShowingTheLookupButton() {
        places.value = listOf(place(id = "p1", name = "어니언 성수"))
        show()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").performClick()
        // the map moving to the place is the app's own move
        appMovesTheMap(lat = 37.5460)
        rule.waitForIdle()
        assertFalse(lastParams!!.following)
        rule.onNodeWithText("이 지역에서 다시 탐색").assertDoesNotExist()
        rule.onNodeWithText("내 위치로").assertExists()
    }

    @Test
    fun theScreenTellsWhereTheUserIsAndWhetherTheMapFollows() {
        show()
        rule.waitForIdle()
        assertTrue(heres.isNotEmpty())
        assertEquals(HERE to true, heres.last())
        userMovesTheMap()
        rule.waitForIdle()
        assertEquals(HERE to false, heres.last())
    }
}
