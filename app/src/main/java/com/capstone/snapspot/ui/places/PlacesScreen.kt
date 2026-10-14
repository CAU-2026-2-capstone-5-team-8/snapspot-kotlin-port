package com.capstone.snapspot.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.distanceMeters
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.mapPlaces
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.placeFacts
import com.capstone.snapspot.ui.components.EmptyBox
import com.capstone.snapspot.ui.components.PlaceCard
import com.capstone.snapspot.ui.components.ScreenHeader
import com.capstone.snapspot.ui.components.WatchStatus
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.Space

/** Saved places, nearest first, with the way to remove one. [places] is expected to be sorted already. */
@Composable
fun PlacesContent(
    places: List<Place>,
    here: Coords?,
    alertsOn: Boolean,
    watching: Int,
    onDelete: (Place) -> Unit,
    onOpenPage: (Place) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onTest: ((Place) -> Unit)? = null,
    /** Shows a place on the app's own map. Without it the "지도" button opens the Kakao page, as it always did. */
    onShowOnMap: ((Place) -> Unit)? = null,
) {
    var pendingDelete by remember { mutableStateOf<Place?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(SnapColor.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.L + 4.dp, vertical = Space.Xl),
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        ScreenHeader(
            title = stringResource(R.string.places_title),
            summary = when {
                places.isEmpty() -> null
                here != null -> stringResource(R.string.places_count_sorted, places.size)
                else -> stringResource(R.string.places_count, places.size)
            },
        )
        WatchStatus(on = alertsOn, watching = watching)

        if (places.isEmpty()) {
            EmptyBox(stringResource(R.string.places_empty_title), stringResource(R.string.places_empty_body))
        } else {
            places.forEach { p ->
                val distance = here?.let { distanceMeters(it, Coords(p.latitude, p.longitude)) }
                PlaceCard(
                    place = p,
                    distance = distance,
                    near = isNear(distance, p.radius),
                    facts = placeFacts(p, now),
                    onDelete = { pendingDelete = p },
                    onMap = when {
                        // A place that has ended is not drawn on the map, so there is nothing to show there.
                        onShowOnMap != null -> if (mapPlaces(listOf(p), now).isNotEmpty()) ({ onShowOnMap(p) }) else null
                        p.placeUrl != null -> ({ onOpenPage(p) })
                        else -> null
                    },
                    onKakao = if (onShowOnMap != null && p.placeUrl != null) ({ onOpenPage(p) }) else null,
                    onTest = onTest?.let { test -> { test(p) } },
                )
            }
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_place_title, p.name)) },
            text = { Text(stringResource(R.string.delete_place_body)) },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.btn_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(p)
                }) { Text(stringResource(R.string.btn_delete)) }
            },
        )
    }
}
