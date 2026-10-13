package com.capstone.snapspot.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capstone.snapspot.data.notify.alertUri
import com.capstone.snapspot.domain.Alert
import com.capstone.snapspot.domain.AlertKind
import com.capstone.snapspot.ui.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The links in notifications lead to the right screens, using the same patterns as the app. */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun launch() {
        rule.setContent {
            nav = TestNavHostController(LocalContext.current as Context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(nav, startDestination = Route.COUPONS) {
                composable(
                    Route.MAP,
                    arguments = listOf(navArgument(Route.PLACE_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
                    deepLinks = listOf(navDeepLink { uriPattern = Route.PLACE_LINK }),
                ) { Text("map") }
                composable(Route.COUPONS) { Text("coupons") }
                composable(
                    Route.COUPON,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = Route.COUPON_LINK }),
                ) { Text("coupon") }
            }
        }
        rule.waitForIdle()
    }

    private fun open(uri: Uri) {
        rule.runOnUiThread { nav.handleDeepLink(Intent(Intent.ACTION_VIEW, uri)) }
        rule.waitForIdle()
    }

    @Test
    fun aCouponAlertOpensThatCoupon() {
        launch()
        open(alertUri(Alert(AlertKind.COUPON, "c_1abc", "t", "b")))
        assertEquals(Route.COUPON, nav.currentDestination?.route)
        assertEquals("c_1abc", nav.currentBackStackEntry?.arguments?.getString("id"))
    }

    @Test
    fun aPlaceAlertOpensTheMapOnThatPlace() {
        launch()
        open(alertUri(Alert(AlertKind.PLACE, "p_9xyz", "t", "b")))
        assertEquals(Route.MAP, nav.currentDestination?.route)
        assertEquals("p_9xyz", nav.currentBackStackEntry?.arguments?.getString(Route.PLACE_ARG))
    }

    @Test
    fun theMapOpenedByHandCarriesNoPlace() {
        launch()
        rule.runOnUiThread { nav.navigate(Route.MAP) }
        rule.waitForIdle()
        assertEquals(Route.MAP, nav.currentDestination?.route)
        assertNull(nav.currentBackStackEntry?.arguments?.getString(Route.PLACE_ARG))
    }

    @Test
    fun anIdThatNeedsEscapingArrivesIntact() {
        launch()
        open(alertUri(Alert(AlertKind.COUPON, "c a/b", "t", "b")))
        assertEquals(Route.COUPON, nav.currentDestination?.route)
        assertEquals("c a/b", nav.currentBackStackEntry?.arguments?.getString("id"))
    }
}
