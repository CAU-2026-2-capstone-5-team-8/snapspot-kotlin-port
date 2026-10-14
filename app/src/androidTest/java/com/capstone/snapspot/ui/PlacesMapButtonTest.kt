package com.capstone.snapspot.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.places.PlacesContent
import com.capstone.snapspot.ui.theme.SnapSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "지도" button of a place card: the app's own map when it is wired, the Kakao page as before when it is not. */
@RunWith(AndroidJUnit4::class)
class PlacesMapButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()
    private val shown = mutableListOf<String>()

    private fun show(p: Place, inApp: Boolean) = rule.setContent {
        SnapSpotTheme {
            PlacesContent(
                listOf(p), HERE, alertsOn = true, watching = 1, onDelete = {},
                onOpenPage = { opened += it.id },
                now = NOW,
                onShowOnMap = if (inApp) ({ shown += it.id }) else null,
            )
        }
    }

    @Test
    fun theMapButtonShowsThePlaceOnTheAppsOwnMap() {
        show(place(id = "p1"), inApp = true)
        rule.onNodeWithText("지도").performClick()
        assertEquals(listOf("p1"), shown)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun theKakaoPageIsAnExtraButton() {
        show(place(id = "p1"), inApp = true)
        rule.onNodeWithText("카카오 상세").performClick()
        assertEquals(listOf("p1"), opened)
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun aPlaceWithoutAKakaoPageStillHasTheMapButtonButNoKakaoButton() {
        show(place(id = "p1", placeUrl = null), inApp = true)
        rule.onNodeWithText("지도").assertExists()
        rule.onNodeWithText("카카오 상세").assertDoesNotExist()
    }

    @Test
    fun aPlaceThatHasEndedIsNotOnTheMapSoItHasNoMapButton() {
        show(place(id = "p1", endDate = "2026-10-01"), inApp = true)
        rule.onNodeWithText("지도").assertDoesNotExist()
        rule.onNodeWithText("카카오 상세").assertExists()
    }

    @Test
    fun withoutTheNewCallbackTheMapButtonOpensTheKakaoPageAsBefore() {
        show(place(id = "p1"), inApp = false)
        rule.onNodeWithText("카카오 상세").assertDoesNotExist()
        rule.onNodeWithText("지도").performClick()
        assertEquals(listOf("p1"), opened)
    }
}
