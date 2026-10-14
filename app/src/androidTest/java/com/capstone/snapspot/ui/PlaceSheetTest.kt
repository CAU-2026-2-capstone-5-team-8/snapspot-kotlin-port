package com.capstone.snapspot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.map.MapContent
import com.capstone.snapspot.ui.map.MapParams
import com.capstone.snapspot.ui.map.MapSupport
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The short description of a place that opens over the map. Dates are far out so "now" does not matter. */
@RunWith(AndroidJUnit4::class)
class PlaceSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private var lastParams: MapParams? = null
    private val opened = mutableListOf<String>()

    private val stubMap: @androidx.compose.runtime.Composable (MapParams, Modifier) -> Unit = { p, _ ->
        lastParams = p
        Column { Text("map:selected=${p.selectedId}") }
    }

    private fun show(places: List<Place>, justSavedId: String? = null) = rule.setContent {
        SnapSpotTheme {
            MapContent(
                places = places,
                here = HERE,
                win = windowState(),
                alertsOn = true,
                watching = 1,
                justSavedId = justSavedId,
                onJustSavedShown = {},
                mapSupport = MapSupport.READY,
                map = stubMap,
                onOpenPlacePage = { opened += it.id },
            )
        }
    }

    private val popup = place(id = "pop", name = "산리오 팝업", note = "굿즈 사전 줄서기", endDate = "2099-10-31", placeUrl = "http://place.map.kakao.com/2")
        .copy(category = "팝업")
    private val cafe = place(id = "cafe", name = "어니언 성수", note = "팡도르 추천", endDate = null, placeUrl = null)

    @Test
    fun tappingAPinOpensTheDescriptionWithCategoryEndDateAndNote() {
        show(listOf(popup, cafe))
        rule.runOnUiThread { lastParams!!.onSelect("pop") }
        rule.waitForIdle()
        rule.onNodeWithText("굿즈 사전 줄서기").assertExists()
        rule.onNodeWithText("10월 31일까지").assertExists()
        // "팝업" is also on the rail card, so look for the one node that has it together with the end date: the sheet.
        rule.onNode(hasText("팝업") and hasText("10월 31일까지")).assertExists()
        rule.onNodeWithText("카카오 상세").assertExists()
    }

    @Test
    fun aPlaceWithoutAnEndDateOrKakaoPageLeavesThoseOut() {
        show(listOf(popup, cafe))
        rule.runOnUiThread { lastParams!!.onSelect("cafe") }
        rule.waitForIdle()
        rule.onNodeWithText("팡도르 추천").assertExists()
        rule.onNodeWithText("카카오 상세").assertDoesNotExist()
        rule.onNodeWithText("까지", substring = true).assertDoesNotExist()
    }

    @Test
    fun theKakaoButtonOpensTheKakaoPage() {
        show(listOf(popup, cafe))
        rule.runOnUiThread { lastParams!!.onSelect("pop") }
        rule.waitForIdle()
        rule.onNodeWithText("카카오 상세").performClick()
        assertEquals(listOf("pop"), opened)
    }

    @Test
    fun closeButtonClosesIt() {
        show(listOf(popup, cafe))
        rule.runOnUiThread { lastParams!!.onSelect("pop") }
        rule.waitForIdle()
        rule.onNodeWithText("굿즈 사전 줄서기").assertExists()
        rule.onNodeWithText("닫기").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("굿즈 사전 줄서기").assertDoesNotExist()
    }

    @Test
    fun tappingACardOnTheRailDoesNotOpenIt() {
        show(listOf(popup, cafe))
        rule.onNodeWithContentDescription("산리오 팝업, 지도에서 보기").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("map:selected=pop").assertExists()
        rule.onNodeWithText("카카오 상세").assertDoesNotExist()
    }

    @Test
    fun aPlaceShownFromAnotherTabOpensItToo() {
        show(listOf(popup, cafe), justSavedId = "pop")
        rule.waitUntil(timeoutMillis = 5_000) { lastParams?.selectedId == "pop" }
        rule.waitForIdle()
        rule.onNodeWithText("굿즈 사전 줄서기").assertExists()
    }
}
