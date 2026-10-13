package com.capstone.snapspot.ui

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.ui.nav.Route
import com.capstone.snapspot.ui.nav.finishAdd
import com.capstone.snapspot.ui.nav.goToTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The same routes as the app, with plain text screens, to test moving between them. */
@RunWith(AndroidJUnit4::class)
class NavActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun launch() {
        rule.setContent {
            nav = TestNavHostController(LocalContext.current as Context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(nav, startDestination = Route.MAP) {
                for (r in listOf(Route.MAP, Route.COUPONS, Route.LIST, Route.STATUS, Route.ADD)) {
                    composable(r) { Text("screen:$r") }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun routes() = nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Test
    fun savingAPlaceFromAddLandsOnTheMapWithoutAnAddScreenLeftOver() {
        launch()
        rule.runOnUiThread { nav.navigate(Route.ADD) }
        rule.waitForIdle()
        assertEquals(Route.ADD, nav.currentDestination?.route)

        rule.runOnUiThread { nav.finishAdd(Route.MAP) }
        rule.waitForIdle()

        assertEquals(Route.MAP, nav.currentDestination?.route)
        assertFalse("add must not come back", Route.ADD in routes())
    }

    @Test
    fun savingACouponFromAddLandsOnTheCouponsTab() {
        launch()
        rule.runOnUiThread { nav.navigate(Route.ADD) }
        rule.waitForIdle()
        rule.runOnUiThread { nav.finishAdd(Route.COUPONS) }
        rule.waitForIdle()

        assertEquals(Route.COUPONS, nav.currentDestination?.route)
        assertFalse(Route.ADD in routes())
    }

    @Test
    fun addingTwiceInARowStillLandsOnTheTargetEachTime() {
        launch()
        repeat(2) {
            rule.runOnUiThread { nav.navigate(Route.ADD) }
            rule.waitForIdle()
            rule.runOnUiThread { nav.finishAdd(Route.MAP) }
            rule.waitForIdle()
            assertEquals(Route.MAP, nav.currentDestination?.route)
            assertFalse(Route.ADD in routes())
        }
    }

    @Test
    fun tabsSwitchAndComeBackToTheMap() {
        launch()
        rule.runOnUiThread { nav.goToTab(Route.LIST) }
        rule.waitForIdle()
        assertEquals(Route.LIST, nav.currentDestination?.route)
        rule.runOnUiThread { nav.goToTab(Route.MAP) }
        rule.waitForIdle()
        assertEquals(Route.MAP, nav.currentDestination?.route)
    }

    @Test
    fun closingAddWithBackReturnsToTheTabItCameFrom() {
        launch()
        rule.runOnUiThread { nav.goToTab(Route.COUPONS) }
        rule.waitForIdle()
        rule.runOnUiThread { nav.navigate(Route.ADD) }
        rule.waitForIdle()
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        assertEquals(Route.COUPONS, nav.currentDestination?.route)
    }
}
