package com.capstone.snapspot.ui.nav

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Opens a tab, replacing whatever is on top (including the add screen) in one navigation step. */
fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Leaves the add screen for [target]. The add screen is removed without saving its state: with the tab
 * navigation above it would be saved and then restored when the start tab opens, bringing back an empty
 * add screen.
 */
fun NavHostController.finishAdd(target: String) {
    popBackStack(Route.ADD, inclusive = true)
    if (currentDestination?.route != target) goToTab(target)
}
