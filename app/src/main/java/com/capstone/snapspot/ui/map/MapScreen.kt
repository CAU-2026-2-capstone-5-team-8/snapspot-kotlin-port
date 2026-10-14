package com.capstone.snapspot.ui.map

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.MapFollow
import com.capstone.snapspot.domain.couponsForBrand
import com.capstone.snapspot.domain.dDay
import com.capstone.snapspot.domain.daysLeft
import com.capstone.snapspot.domain.distanceMeters
import com.capstone.snapspot.domain.formatDistance
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.limitBranches
import com.capstone.snapspot.domain.mapPlaces
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.domain.visibleBranches
import com.capstone.snapspot.ui.components.Shot
import com.capstone.snapspot.ui.theme.LocalReduceMotion
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The map tab: the map full screen, a "SnapSpot" chip and a status pill over it, and a rail of cards below.
 * Pins and cards stay in step: tapping one selects the other. [map] draws the map itself and is replaceable,
 * so the overlays can be tested without the Kakao SDK.
 */
@Composable
fun MapContent(
    places: List<Place>,
    here: Coords?,
    win: WindowState?,
    alertsOn: Boolean,
    watching: Int,
    justSavedId: String?,
    onJustSavedShown: () -> Unit,
    mapSupport: MapSupport,
    modifier: Modifier = Modifier,
    map: @Composable (MapParams, Modifier) -> Unit = { p, m -> KakaoMapView(p, m) },
    onSetupAlerts: (() -> Unit)? = null,
    /** Coupons decide which branch badges stay: a brand with no active coupon has none. */
    coupons: List<Coupon> = emptyList(),
    /** Branches found by the map's own lookup, drawn next to the watch plan's. */
    mapBranches: Map<String, BranchRef> = emptyMap(),
    /** The user is at this place while the map is open; true when the map is following. */
    onHere: (Coords, Boolean) -> Unit = { _, _ -> },
    /** "이 지역에서 다시 탐색" with the center of what the map shows. */
    onSearchHere: (Coords) -> Unit = {},
    onOpenCoupon: (String) -> Unit = {},
    /** The Kakao page of a place, from its description. */
    onOpenPlacePage: (Place) -> Unit = {},
) {
    // From here on, `places` is what the map draws: places that have ended are not on it (the saved list keeps them).
    // Looked at again when the lists change and when the screen comes back, which is when a date can have passed.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val now = remember(places, coupons, resumes) { System.currentTimeMillis() }
    val places = remember(places, now) { mapPlaces(places, now) }

    var follow by remember { mutableStateOf(MapFollow()) }
    var camera by remember { mutableStateOf<Coords?>(null) }
    // Only the branches near the user (or near the middle of the map once it has been moved by hand) are drawn.
    val branchCenter = if (follow.following) here else camera ?: here
    val branches = remember(mapBranches, win, coupons, now, branchCenter) {
        limitBranches(visibleBranches(mapBranches, win?.branches.orEmpty(), coupons, now), branchCenter)
    }
    // The brand whose coupons are listed. The list is read from the coupons each time, so it is never out of date.
    var sheetBrand by remember { mutableStateOf<String?>(null) }
    val sheetCoupons = sheetBrand?.let { couponsForBrand(coupons, it, now) }.orEmpty()
    LaunchedEffect(here, follow.following, coupons) { if (here != null) onHere(here, follow.following) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<Exception?>(null) }
    var railHeightPx by remember { mutableIntStateOf(0) }
    // The place whose short description is open. It opens for a pin and for a place shown on the map, not for the rail.
    var sheetPlaceId by remember { mutableStateOf<String?>(null) }

    fun select(id: String?, fromMap: Boolean) {
        selectedId = id
        if (id == null) return
        val i = places.indexOfFirst { it.id == id }
        if (i < 0) return
        if (fromMap) sheetPlaceId = id
        follow = follow.placeFocused() // looking at a place ends following, or the next position would pull the map back
        if (fromMap) scope.launch { if (reduceMotion) listState.scrollToItem(i) else listState.animateScrollToItem(i) }
        val p = places[i]
        focus = MapFocus(p.latitude, p.longitude, ++nonce)
    }

    // A place that was just added gets selected, so the user sees where it landed.
    LaunchedEffect(justSavedId, places) {
        if (justSavedId != null && places.any { it.id == justSavedId }) {
            delay(400)
            select(justSavedId, fromMap = true)
            // Only now: clearing the marker changes this effect's key, which would cancel it mid-delay.
            onJustSavedShown()
        }
    }

    // When the user swipes the rail and it comes to rest on another card, the map follows it. Scrolls the app
    // does itself (to the card of a tapped pin) must not count, or the last card, which cannot reach the left
    // edge, would hand the selection back to the first one.
    var swipedByUser by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) swipedByUser = true }
    }
    LaunchedEffect(places) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect {
            if (!swipedByUser) return@collect
            swipedByUser = false
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val resting = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) } ?: return@collect
            places.getOrNull(resting.index)?.takeIf { it.id != selectedId }?.let { select(it.id, fromMap = false) }
        }
    }

    val statusTop = WindowInsets.statusBars.getTop(density)
    val topInset = statusTop + with(density) { 56.dp.roundToPx() }
    val bottomInset = railHeightPx + with(density) { Space.L.roundToPx() }

    Box(modifier.fillMaxSize().background(SnapColor.Paper)) {
        when {
            mapSupport == MapSupport.NO_KEY -> MapProblem(stringResource(R.string.map_error_title), stringResource(R.string.map_no_key_body))
            mapSupport == MapSupport.UNSUPPORTED -> MapProblem(stringResource(R.string.map_error_title), stringResource(R.string.map_unsupported_body))
            failure != null -> MapProblem(stringResource(R.string.map_error_title), stringResource(R.string.map_error_body))
            else -> {
                val mapDesc = stringResource(R.string.map_desc)
                map(
                    MapParams(
                        places = places,
                        here = here,
                        win = win,
                        selectedId = selectedId,
                        focus = focus,
                        topInsetPx = topInset,
                        bottomInsetPx = bottomInset,
                        onSelect = { id -> select(id, fromMap = true) },
                        onFailure = { e ->
                            Log.w("SnapSpot", "Kakao map failed to load", e)
                            failure = e
                        },
                        branches = branches,
                        following = follow.following,
                        onBranch = { key ->
                            val list = couponsForBrand(coupons, key, now)
                            when (list.size) {
                                0 -> Unit
                                1 -> onOpenCoupon(list[0].id)
                                else -> sheetBrand = key
                            }
                        },
                        onCamera = { lat, lng, zoom, byUser ->
                            camera = Coords(lat, lng)
                            follow = follow.cameraMoved(byUser, zoom)
                        },
                    ),
                    Modifier.semantics { contentDescription = mapDesc },
                )
            }
        }

        // Top overlay: the wordmark and whether alerts are on.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = with(density) { statusTop.toDp() } + Space.S, start = Space.L, end = Space.L),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SnapColor.Surface)
                    .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(12.dp))
                    .padding(horizontal = Space.M, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.wordmark), style = SnapType.Distance.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
            // While alerts are off, the pill opens the walk-through that turns them on.
            StatusPill(alertsOn, watching, onClick = if (alertsOn) null else onSetupAlerts)
        }

        // Under the top row: look for branches where the user moved the map, or ask for a closer view.
        if (follow.showSearchHere || follow.zoomHint) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = with(density) { topInset.toDp() } + Space.S)) {
                if (follow.showSearchHere) {
                    MapPill(stringResource(R.string.map_search_here)) {
                        camera?.let(onSearchHere)
                        follow = follow.searched()
                    }
                } else {
                    MapPill(stringResource(R.string.map_zoom_in), onClick = null)
                }
            }
        }

        // Back to the user's position, and following again.
        if (!follow.following && here != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.L, bottom = with(density) { railHeightPx.toDp() } + Space.L + Space.M),
            ) {
                MapPill(stringResource(R.string.map_my_location)) { follow = follow.backToMe() }
            }
        }

        // Bottom overlay: the card rail, or a hint when nothing is saved yet.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.L)
                .onSizeChanged { railHeightPx = it.height },
        ) {
            if (places.isEmpty()) {
                EmptyCard(Modifier.padding(horizontal = Space.Xl))
            } else {
                val cardWidth = LocalConfiguration.current.screenWidthDp.dp - Space.Xl * 2 - Space.L
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    contentPadding = PaddingValues(horizontal = Space.Xl),
                    horizontalArrangement = Arrangement.spacedBy(Space.M),
                ) {
                    items(places, key = { it.id }) { p ->
                        val distance = here?.let { distanceMeters(it, Coords(p.latitude, p.longitude)) }
                        RailCard(
                            place = p,
                            width = cardWidth,
                            distance = distance,
                            near = isNear(distance, p.radius),
                            selected = p.id == selectedId,
                            onClick = { select(p.id, fromMap = false) },
                        )
                    }
                }
            }
        }

        // Several coupons of one brand: soonest-expiring first, each opens its coupon.
        if (sheetCoupons.size > 1) {
            CouponSheet(
                coupons = sheetCoupons,
                now = now,
                onOpen = { id ->
                    sheetBrand = null
                    onOpenCoupon(id)
                },
                onClose = { sheetBrand = null },
            )
        }

        // A pin, or a place shown from the places tab: what it is and until when.
        places.firstOrNull { it.id == sheetPlaceId }?.let { p ->
            PlaceSheet(
                place = p,
                now = now,
                onOpenPage = if (p.placeUrl != null) ({ onOpenPlacePage(p) }) else null,
                onClose = { sheetPlaceId = null },
            )
        }
    }
}

@Composable
private fun StatusPill(alertsOn: Boolean, watching: Int, onClick: (() -> Unit)? = null) {
    // When it can be tapped, the touch target grows to the minimum size without changing how the pill looks.
    Box(
        if (onClick != null) Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick) else Modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SnapColor.Surface)
                .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(999.dp))
                .padding(horizontal = Space.M, vertical = Space.S),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (alertsOn) SnapColor.Green else SnapColor.Amber))
            Text(
                if (alertsOn) stringResource(R.string.watching_count, watching) else stringResource(R.string.alerts_setup_needed),
                style = SnapType.Body.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

/** A small rounded button (or, without [onClick], a hint) floating over the map. */
@Composable
private fun MapPill(text: String, onClick: (() -> Unit)?) {
    Box(
        if (onClick != null) Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick) else Modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = SnapType.Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(SnapColor.Surface)
                .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(999.dp))
                .padding(horizontal = Space.L, vertical = Space.S),
        )
    }
}

/** The coupons of one brand over the map, soonest-expiring first. Tapping outside closes it. */
@Composable
private fun CouponSheet(coupons: List<Coupon>, now: Long, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val closeDesc = stringResource(R.string.map_branch_sheet_close)
    Box(
        Modifier
            .fillMaxSize()
            .background(SnapColor.Ink.copy(alpha = 0.35f))
            .clickable(onClickLabel = closeDesc, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(SnapColor.Surface)
                .clickable(enabled = false, onClick = {})
                .padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            Text(stringResource(R.string.map_branch_sheet_title, coupons.first().brand), style = SnapType.Title)
            for (c in coupons) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onOpen(c.id) }
                        .padding(Space.M),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.benefit, style = SnapType.Body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    dDay(daysLeft(c, now))?.let { Text(it, style = SnapType.Label.copy(color = SnapColor.Green)) }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SnapColor.Surface)
            .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(16.dp))
            .padding(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.Xs),
    ) {
        Text(stringResource(R.string.map_empty_title), style = SnapType.Title)
        Text(stringResource(R.string.map_empty_body), style = SnapType.Meta)
    }
}

@Composable
private fun RailCard(place: Place, width: androidx.compose.ui.unit.Dp, distance: Double?, near: Boolean, selected: Boolean, onClick: () -> Unit) {
    val desc = stringResource(R.string.show_on_map_desc, place.name)
    Row(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(SnapColor.Surface)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SnapColor.Ink else SnapColor.Line), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(Space.M),
        horizontalArrangement = Arrangement.spacedBy(Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Shot(place.imageUri, 56.dp, 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(place.name, style = SnapType.Title.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(place.category, style = SnapType.Label.copy(color = SnapColor.Green))
            Text(place.address.ifEmpty { stringResource(R.string.no_address) }, style = SnapType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (near) {
            Text(
                stringResource(R.string.near_tag),
                style = SnapType.Distance.copy(fontSize = 15.sp, lineHeight = 20.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SnapColor.Highlight).padding(horizontal = Space.S, vertical = 2.dp),
            )
        } else if (distance != null) {
            Text(formatDistance(distance), style = SnapType.Distance.copy(fontSize = 18.sp, lineHeight = 24.sp))
        }
    }
}

/** What stands in for the map when it cannot be shown: the rest of the app keeps working around it. */
@Composable
private fun MapProblem(title: String, body: String) {
    Box(Modifier.fillMaxSize().background(SnapColor.Paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(horizontal = Space.Xl)
                .clip(RoundedCornerShape(16.dp))
                .background(SnapColor.AmberSoft)
                .padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.S),
        ) {
            Text(title, style = SnapType.Title.copy(color = SnapColor.Amber))
            Text(body, style = SnapType.Body.copy(fontSize = 14.sp, color = SnapColor.Amber))
        }
    }
}
