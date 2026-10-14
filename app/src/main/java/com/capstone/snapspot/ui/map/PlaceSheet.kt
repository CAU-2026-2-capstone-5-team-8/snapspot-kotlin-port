package com.capstone.snapspot.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.dDay
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.domain.placeInfo
import com.capstone.snapspot.ui.components.ButtonKind
import com.capstone.snapspot.ui.components.Shot
import com.capstone.snapspot.ui.components.SnapButton
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/**
 * A short description of one place over the map: its screenshot, category, end date and note. Tapping outside
 * closes it. [onOpenPage] is null for a place without a Kakao page.
 */
@Composable
fun PlaceSheet(place: Place, now: Long, onOpenPage: (() -> Unit)?, onClose: () -> Unit) {
    val info = placeInfo(place, now)
    val closeDesc = stringResource(R.string.close)
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
                .navigationBarsPadding()
                .padding(Space.L),
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                Shot(place.imageUri, 72.dp, 96.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    Text(info.name, style = SnapType.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(info.category, style = SnapType.Label.copy(color = SnapColor.Green))
                    info.endLabel?.let { label ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = SnapType.Body)
                            dDay(info.daysLeft)?.let { Text(it, style = SnapType.Label.copy(color = SnapColor.Green)) }
                        }
                    }
                    info.note?.let { Text(it, style = SnapType.Body.copy(fontSize = 14.sp), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onOpenPage != null) SnapButton(stringResource(R.string.btn_kakao_detail), onOpenPage, kind = ButtonKind.Quiet)
                SnapButton(closeDesc, onClose, kind = ButtonKind.Secondary)
            }
        }
    }
}
