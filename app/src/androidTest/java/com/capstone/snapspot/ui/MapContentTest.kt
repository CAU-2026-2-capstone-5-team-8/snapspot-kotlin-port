package com.capstone.snapspot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.map.MapContent
import com.capstone.snapspot.ui.map.MapParams
import com.capstone.snapspot.ui.map.MapSupport
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The map tab's overlays and rail, with a stand-in for the Kakao map so no SDK or network is involved. */
@RunWith(AndroidJUnit4::class)
class MapContentTest {
    @get:Rule
    val rule = createComposeRule()

    private var lastParams: MapParams? = null

    /** Shows what the screen asked of the map, and lets a test act as the map (tap a pin, or fail). */
    private val stubMap: @androidx.compose.runtime.Composable (MapParams, Modifier) -> Unit = { p, _ ->
        lastParams = p
        Column {
            Text("map:selected=${p.selectedId}")
            Text("map:focus=${p.focus?.nonce ?: 0}")
            Text("map:places=${p.places.size}")
        }
    }

    private fun show(
        places: List<Place>,
        here: Coords? = HERE,
        alertsOn: Boolean = true,
        watching: Int = 3,
        justSavedId: String? = null,
        onJustSavedShown: () -> Unit = {},
        mapSupport: MapSupport = MapSupport.READY,
    ) = rule.setContent {
        SnapSpotTheme {
            MapContent(
                places = places,
                here = here,
                win = windowState(),
                alertsOn = alertsOn,
                watching = watching,
                justSavedId = justSavedId,
                onJustSavedShown = onJustSavedShown,
                mapSupport = mapSupport,
                map = stubMap,
            )
        }
    }

    private val onion = place(id = "p1", name = "어니언 성수", lat = 37.5460)
    private val far = place(id = "p2", name = "더현대 서울", lat = 37.60, address = "서울 영등포구 여의대로 108")

    @Test
    fun withoutPlacesTheMapShowsTheFirstPlaceHint() {
        show(emptyList())
        rule.onNodeWithText("SnapSpot").assertExists()
        rule.onNodeWithText("첫 장소를 꽂아 보세요").assertExists()
        rule.onNodeWithText("map:places=0").assertExists()
    }

    @Test
    fun theStatusPillSaysHowManyAreWatchedOrThatSetupIsNeeded() {
        show(emptyList(), alertsOn = true, watching = 7)
        rule.onNodeWithText("7곳 지켜보는 중").assertExists()
    }

    @Test
    fun theStatusPillAsksForSetupWhenAlertsAreOff() {
        show(emptyList(), alertsOn = false)
        rule.onNodeWithText("알림 설정 필요").assertExists()
    }

    @Test
    fun theRailShowsACardPerPlaceWithNearTagOrDistance() {
        show(listOf(onion, far))
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").assertExists()
        rule.onNodeWithContentDescription("더현대 서울, 지도에서 보기").assertExists()
        rule.onNodeWithText("근처").assertExists() // 어니언 성수 is inside its 200 m radius
        rule.onNodeWithText("서울 성동구 아차산로9길 8", substring = true).assertExists()
    }

    @Test
    fun theMapGetsEveryPlaceAndNothingIsSelectedAtFirst() {
        show(listOf(onion, far))
        rule.onNodeWithText("map:places=2").assertExists()
        rule.onNodeWithText("map:selected=null").assertExists()
        assertEquals(2, lastParams!!.places.size)
    }

    @Test
    fun tappingACardSelectsThatPlaceAndMovesTheMapToIt() {
        show(listOf(onion, far))
        rule.onNodeWithContentDescription("더현대 서울, 지도에서 보기").performClick()
        rule.onNodeWithText("map:selected=p2").assertExists()
        rule.onNodeWithText("map:focus=1").assertExists()
        assertEquals(37.60, lastParams!!.focus!!.lat, 1e-9)
    }

    @Test
    fun tappingAPinOnTheMapSelectsItToo() {
        show(listOf(onion, far))
        rule.runOnUiThread { lastParams!!.onSelect("p1") }
        rule.onNodeWithText("map:selected=p1").assertExists()
        rule.onNodeWithText("map:focus=1").assertExists()
    }

    @Test
    fun tappingEmptyMapClearsTheSelection() {
        show(listOf(onion, far))
        rule.onNodeWithContentDescription("더현대 서울, 지도에서 보기").performClick()
        rule.runOnUiThread { lastParams!!.onSelect(null) }
        rule.onNodeWithText("map:selected=null").assertExists()
    }

    @Test
    fun aJustSavedPlaceGetsSelectedAndTheMapMovesToIt() {
        var shown = 0
        show(listOf(onion, far), justSavedId = "p2", onJustSavedShown = { shown++ })
        rule.waitUntil(timeoutMillis = 5_000) { lastParams?.selectedId == "p2" }
        rule.onNodeWithText("map:selected=p2").assertExists()
        assertEquals(37.60, lastParams!!.focus!!.lat, 1e-9)
        assertTrue(shown >= 1)
    }

    @Test
    fun aJustSavedPlaceStillGetsSelectedWhenTheScreenClearsTheMarkerRightAway() {
        // The real screen clears "just saved" as soon as it has been shown, which must not cancel the selection.
        rule.setContent {
            SnapSpotTheme {
                var saved by remember { mutableStateOf<String?>("p2") }
                MapContent(
                    places = listOf(onion, far),
                    here = HERE,
                    win = windowState(),
                    alertsOn = true,
                    watching = 3,
                    justSavedId = saved,
                    onJustSavedShown = { saved = null },
                    mapSupport = MapSupport.READY,
                    map = stubMap,
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) { lastParams?.selectedId == "p2" }
        rule.waitForIdle()
        assertEquals("p2", lastParams!!.selectedId)
        assertEquals(37.60, lastParams!!.focus!!.lat, 1e-9)
    }

    @Test
    fun aJustSavedLastPlaceStaysSelectedEvenThoughItsCardCannotReachTheLeftEdge() {
        show(listOf(onion, far), justSavedId = "p2")
        rule.waitUntil(timeoutMillis = 5_000) { lastParams?.selectedId == "p2" }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals("p2", lastParams!!.selectedId)
    }

    @Test
    fun swipingTheRailByHandSelectsTheCardItComesToRestOn() {
        show(listOf(onion, far))
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 5_000) { lastParams?.selectedId == "p2" }
        assertEquals("p2", lastParams!!.selectedId)
    }

    @Test
    fun aJustSavedIdThatIsNotInTheListIsIgnored() {
        var shown = 0
        show(listOf(onion), justSavedId = "gone", onJustSavedShown = { shown++ })
        rule.waitForIdle()
        assertEquals(0, shown)
        rule.onNodeWithText("map:selected=null").assertExists()
    }

    @Test
    fun withoutAKeyTheMapIsReplacedByAnExplanationAndTheRailStillWorks() {
        show(listOf(onion), mapSupport = MapSupport.NO_KEY)
        rule.onNodeWithText("지도를 불러오지 못했어요").assertExists()
        rule.onNodeWithText("KAKAO_NATIVE_KEY", substring = true).assertExists()
        rule.onAllNodesWithText("map:places", substring = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").assertExists()
    }

    @Test
    fun onADeviceThatCannotLoadTheMapTheScreenSaysWhyAndTheRailStillWorks() {
        show(listOf(onion), mapSupport = MapSupport.UNSUPPORTED)
        rule.onNodeWithText("지도를 불러오지 못했어요").assertExists()
        rule.onNodeWithText("ARM", substring = true).assertExists()
        rule.onAllNodesWithText("map:places", substring = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").assertExists()
    }

    @Test
    fun whenTheMapFailsToLoadTheScreenSaysSoInsteadOfBreaking() {
        show(listOf(onion))
        rule.runOnUiThread { lastParams!!.onFailure(IllegalStateException("auth failed")) }
        rule.onNodeWithText("지도를 불러오지 못했어요").assertExists()
        rule.onNodeWithText("키 해시", substring = true).assertExists()
        rule.onNodeWithContentDescription("어니언 성수, 지도에서 보기").assertExists()
        assertTrue(lastParams != null)
    }
}
