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
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.distanceMeters
import com.capstone.snapspot.domain.formatDistance
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.model.WindowState
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
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val listState = rememberLazyListState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<Exception?>(null) }
    var railHeightPx by remember { mutableIntStateOf(0) }

    fun select(id: String?, fromMap: Boolean) {
        selectedId = id
        if (id == null) return
        val i = places.indexOfFirst { it.id == id }
        if (i < 0) return
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
