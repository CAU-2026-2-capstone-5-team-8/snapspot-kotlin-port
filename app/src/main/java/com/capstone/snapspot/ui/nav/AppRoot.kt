package com.capstone.snapspot.ui.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.ui.AppViewModel
import com.capstone.snapspot.ui.add.AddRoute
import com.capstone.snapspot.ui.add.AddViewModel
import com.capstone.snapspot.ui.coupon.CouponDetailContent
import com.capstone.snapspot.ui.coupon.CouponSwipe
import com.capstone.snapspot.ui.coupons.CouponsContent
import com.capstone.snapspot.ui.map.MapBranchViewModel
import com.capstone.snapspot.ui.map.MapContent
import com.capstone.snapspot.ui.map.MapSupport
import com.capstone.snapspot.ui.permissions.PermissionGuideRoute
import com.capstone.snapspot.ui.permissions.openAppSettings
import com.capstone.snapspot.ui.places.PlacesContent
import com.capstone.snapspot.ui.status.StatusContent
import com.capstone.snapspot.ui.theme.LocalReduceMotion
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.enterOr
import com.capstone.snapspot.ui.theme.exitOr
import kotlinx.coroutines.launch

private fun Context.openPage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser to open it with: nothing more to do.
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val logShareTitle = stringResource(R.string.log_share_title)
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    // Created here, outside the NavHost, so every screen shares one of each.
    val addVm: AddViewModel = hiltViewModel()
    val app: AppViewModel = hiltViewModel()
    val mapVm: MapBranchViewModel = hiltViewModel()

    val places by app.places.collectAsStateWithLifecycle()
    val mapBranches by mapVm.state.collectAsStateWithLifecycle()
    val coupons by app.coupons.collectAsStateWithLifecycle()
    val here by app.here.collectAsStateWithLifecycle()
    val win by app.win.collectAsStateWithLifecycle()
    val perms by app.perms.collectAsStateWithLifecycle()
    val justSavedId by app.justSavedId.collectAsStateWithLifecycle()
    val logCount by app.eventCount.collectAsStateWithLifecycle()
    var needsSetup by remember { mutableStateOf(false) }
    val watching = win?.byGroup.orEmpty().values.sum()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            addVm.start(uri.toString())
            nav.navigate(Route.ADD) { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) { app.start() }
    // First launch with something still off: walk the user through it once. Later the status tab offers it again.
    val guideSeen by app.guideSeen.collectAsStateWithLifecycle()
    LaunchedEffect(guideSeen) {
        if (guideSeen == false) {
            app.markGuideSeen()
            if (app.perms.value.missingAny) nav.navigate(Route.PERMISSIONS) { launchSingleTop = true }
        }
    }
    // Coming back from system settings can change what is allowed, and so can the phone's location switch.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { app.refreshEnvironment() }
    // A notification tapped while the app is open reaches the single running activity as a new intent; navigation
    // only reads the intent the activity started with, so hand it the later ones too.
    DisposableEffect(context, nav) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().firstOrNull()
        val listener = Consumer<Intent> { nav.handleDeepLink(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    // Follow the user only while the app is on screen; the OS geofences cover the rest.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { app.onForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { app.onBackground() }

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    Column(Modifier.fillMaxSize().background(SnapColor.Paper)) {
        NavHost(
            navController = nav,
            startDestination = Route.MAP,
            modifier = Modifier.weight(1f),
            enterTransition = { enterOr(reduce) { fadeIn() } },
            exitTransition = { exitOr(reduce) { fadeOut() } },
        ) {
            composable(
                Route.MAP,
                arguments = listOf(navArgument(Route.PLACE_ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
                // A place alert opens the map on that place.
                deepLinks = listOf(navDeepLink { uriPattern = Route.PLACE_LINK }),
            ) { entry ->
                val focusId = entry.arguments?.getString(Route.PLACE_ARG)
                LaunchedEffect(focusId) { focusId?.let(app::showOnMap) }
                MapContent(
                    places = places,
                    here = here,
                    win = win,
                    alertsOn = perms.alertsOn,
                    watching = watching,
                    justSavedId = justSavedId,
                    onJustSavedShown = app::clearJustSaved,
                    mapSupport = MapSupport.current,
                    onSetupAlerts = { nav.navigate(Route.PERMISSIONS) { launchSingleTop = true } },
                    coupons = coupons,
                    mapBranches = mapBranches.branches,
                    onHere = { at, following -> mapVm.onHere(at, following, coupons) },
                    onSearchHere = { center -> mapVm.searchAt(center, coupons) },
                    onOpenCoupon = { nav.navigate(Route.coupon(it)) },
                    onOpenPlacePage = { p -> p.placeUrl?.let(context::openPage) },
                )
            }
            composable(Route.COUPONS) {
                CouponsContent(coupons = coupons, win = win, here = here, onOpen = { nav.navigate(Route.coupon(it.id)) })
            }
            composable(Route.LIST) {
                PlacesContent(
                    places = places,
                    here = here,
                    alertsOn = perms.alertsOn,
                    watching = watching,
                    onDelete = app::deletePlace,
                    onOpenPage = { p -> p.placeUrl?.let(context::openPage) },
                    onShowOnMap = { p ->
                        app.showOnMap(p.id)
                        nav.goToTab(Route.MAP)
                    },
                    onTest = app::testPlaceAlert,
                )
            }
            composable(Route.STATUS) {
                StatusContent(
                    perms = perms,
                    win = win,
                    coupons = coupons,
                    logCount = logCount,
                    onOpenSettings = context::openAppSettings,
                    onExportLog = {
                        scope.launch {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, logShareTitle)
                                putExtra(Intent.EXTRA_TEXT, app.exportLogJson())
                            }
                            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    onClearLog = app::clearLog,
                    onOpenGuide = { nav.navigate(Route.PERMISSIONS) { launchSingleTop = true } },
                )
            }
            composable(
                Route.ADD,
                enterTransition = { enterOr(reduce) { slideInVertically { it } } },
                exitTransition = { exitOr(reduce) { slideOutVertically { it } } },
                popEnterTransition = { enterOr(reduce) { fadeIn() } },
                popExitTransition = { exitOr(reduce) { slideOutVertically { it } } },
            ) {
                AddRoute(
                    vm = addVm,
                    // Coupons live in their own tab; places show up on the map.
                    onSaved = { saved ->
                        app.onSaved(saved)
                        nav.finishAdd(if (saved.kind == Kind.COUPON) Route.COUPONS else Route.MAP)
                    },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                Route.PERMISSIONS,
                enterTransition = { enterOr(reduce) { slideInVertically { it } } },
                exitTransition = { exitOr(reduce) { slideOutVertically { it } } },
                popEnterTransition = { enterOr(reduce) { fadeIn() } },
                popExitTransition = { exitOr(reduce) { slideOutVertically { it } } },
            ) {
                // Closed with popBackStack only: tab navigation would save this screen's state and bring it back.
                PermissionGuideRoute(app = app, onClose = { nav.popBackStack() })
            }
            composable(
                Route.COUPON,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                // A coupon alert opens its coupon through this link, also from a cold start.
                deepLinks = listOf(navDeepLink { uriPattern = Route.COUPON_LINK }),
                enterTransition = { enterOr(reduce) { slideInVertically { it } } },
                exitTransition = { exitOr(reduce) { slideOutVertically { it } } },
                popEnterTransition = { enterOr(reduce) { fadeIn() } },
                popExitTransition = { exitOr(reduce) { slideOutVertically { it } } },
            ) { entry ->
                val id = entry.arguments?.getString("id")
                // Swipe left and right to the other active coupons of the same brand.
                CouponSwipe(coupons, id) { coupon ->
                    CouponDetailContent(
                        coupon = coupon,
                        win = win,
                        here = here,
                        onClose = { nav.popBackStack() },
                        onDelete = { c ->
                            app.deleteCoupon(c)
                            nav.popBackStack()
                        },
                        onUsed = { c ->
                            app.markCouponUsed(c)
                            nav.popBackStack()
                        },
                        onToCoupons = { nav.goToTab(Route.COUPONS) },
                        onTest = app::testCouponAlert,
                    )
                }
            }
        }
        if (route in Route.TABS) {
            TabBar(
                current = route,
                onSelect = nav::goToTab,
                onAdd = {
                    if (addVm.analyzerConfigured) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        needsSetup = true
                    }
                },
            )
        }
    }

    if (needsSetup) {
        AlertDialog(
            onDismissRequest = { needsSetup = false },
            confirmButton = { TextButton(onClick = { needsSetup = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.setup_needed_title)) },
            text = { Text(stringResource(R.string.setup_needed_body)) },
        )
    }
}
