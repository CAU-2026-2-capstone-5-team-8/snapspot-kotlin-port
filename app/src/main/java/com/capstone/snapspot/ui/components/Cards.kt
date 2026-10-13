package com.capstone.snapspot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.BranchDistance
import com.capstone.snapspot.domain.SlotKind
import com.capstone.snapspot.domain.SlotPart
import com.capstone.snapspot.domain.dDay
import com.capstone.snapspot.domain.formatDistance
import com.capstone.snapspot.domain.model.Place
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapFont
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/** One line that says whether alerts will arrive with the app closed. */
@Composable
fun WatchStatus(on: Boolean, watching: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) SnapColor.Green else SnapColor.Line))
        Text(
            stringResource(if (on) R.string.watch_on else R.string.watch_off),
            style = SnapType.Body.copy(fontWeight = FontWeight.Medium),
        )
        if (on && watching > 0) Text(stringResource(R.string.watching_count, watching), style = SnapType.Meta)
    }
}

/** A dashed box for an empty list: what goes here and how to add it. */
@Composable
fun EmptyBox(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = SnapColor.Line,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
            }
            .padding(Space.Xl),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        Text(title, style = SnapType.Body.copy(fontWeight = FontWeight.Medium))
        Text(body, style = SnapType.Meta)
    }
}

/** Both place and coupon cards use the same outline: ink and thicker when something is near right now. */
private fun Modifier.cardFrame(near: Boolean): Modifier =
    this.clip(RoundedCornerShape(16.dp))
        .background(SnapColor.Surface)
        .border(BorderStroke(if (near) 1.5.dp else 1.dp, if (near) SnapColor.Ink else SnapColor.Line), RoundedCornerShape(16.dp))

@Composable
private fun NearBand(text: String) {
    Box(Modifier.fillMaxWidth().background(SnapColor.Highlight).padding(horizontal = Space.L, vertical = Space.S)) {
        Text(text, style = SnapType.Distance.copy(fontSize = 15.sp, lineHeight = 20.sp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceCard(
    place: Place,
    distance: Double?,
    near: Boolean,
    facts: List<String>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMap: (() -> Unit)? = null,
    onTest: (() -> Unit)? = null,
) {
    Column(modifier.cardFrame(near)) {
        if (near) NearBand(stringResource(R.string.near_now_place))
        Column(Modifier.padding(Space.L), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.M)) {
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    Shot(place.imageUri, 72.dp, 96.dp)
                    if (distance != null && !near) {
                        val text = formatDistance(distance)
                        val desc = stringResource(R.string.distance_desc, text)
                        Text(text, style = SnapType.Distance.copy(fontSize = 17.sp, lineHeight = 22.sp), modifier = Modifier.semantics { contentDescription = desc })
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(place.name, style = SnapType.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(place.category, style = SnapType.Label.copy(color = SnapColor.Green))
                    if (place.note.isNotEmpty()) {
                        Text(place.note, style = SnapType.Body.copy(fontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        place.address.ifEmpty { stringResource(R.string.no_address) },
                        style = SnapType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.Xs), verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    facts.forEach { fact ->
                        Text(
                            fact,
                            style = SnapType.Meta.copy(fontSize = 12.sp),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SnapColor.Paper).padding(horizontal = Space.S, vertical = 2.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onMap != null) SnapButton(stringResource(R.string.btn_map), onMap, kind = ButtonKind.Quiet)
                    if (onTest != null) SnapButton(stringResource(R.string.btn_test_alert), onTest, kind = ButtonKind.Quiet)
                    SnapButton(stringResource(R.string.btn_delete), onDelete, kind = ButtonKind.Quiet)
                }
            }
        }
    }
}

/**
 * A coupon as a ticket: a perforation between what it is and where to use it.
 * The yellow band means the same as on place cards: a branch is near right now.
 */
@Composable
fun TicketCard(
    brand: String,
    benefit: String,
    imageUri: String?,
    expiresLabel: String?,
    days: Long?,
    nearest: BranchDistance?,
    near: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val urgent = days != null && days <= 3 && !dimmed
    val desc = stringResource(R.string.open_coupon_desc, brand, benefit)
    Column(
        modifier
            .alpha(if (dimmed) 0.55f else 1f)
            .cardFrame(near)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
    ) {
        if (near && nearest != null) NearBand(stringResource(R.string.near_branch, nearest.name))
        Row(Modifier.padding(start = Space.L, end = Space.L, top = Space.L), horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.Top) {
            Shot(imageUri, 60.dp, 80.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(brand, style = SnapType.Distance.copy(fontSize = 20.sp, lineHeight = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(benefit, style = SnapType.Body.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (expiresLabel != null) Text(expiresLabel, style = SnapType.Meta)
            }
            dDay(days)?.let {
                Text(it, style = SnapType.Distance.copy(fontSize = 18.sp, lineHeight = 24.sp, color = if (urgent) SnapColor.Amber else SnapColor.Green))
            }
        }
        Perforation(Modifier.padding(vertical = Space.M))
        Text(
            when {
                nearest != null -> stringResource(R.string.nearest_branch_line, nearest.name, formatDistance(nearest.distance))
                dimmed -> stringResource(R.string.coupon_off)
                else -> stringResource(R.string.searching_branches)
            },
            style = SnapType.Meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Space.L, end = Space.L, bottom = Space.L),
        )
    }
}

/** The tear line of a ticket: a dashed rule with a half-circle notch cut out of each edge. */
@Composable
private fun Perforation(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.M + 9.dp)
                .height(2.dp)
                .drawBehind {
                    drawLine(
                        color = SnapColor.Line,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                },
        )
        Notch(Modifier.align(Alignment.CenterStart).offset(x = (-9).dp))
        Notch(Modifier.align(Alignment.CenterEnd).offset(x = 9.dp))
    }
}

@Composable
private fun Notch(modifier: Modifier) {
    Box(modifier.size(18.dp).clip(CircleShape).background(SnapColor.Paper).border(BorderStroke(1.dp, SnapColor.Line), CircleShape))
}

/** Brand segments are told apart by shades of ink; saved places keep the action green. */
private val BrandTones = listOf(Color(0xFF1F2B2A), Color(0xFF4E6B63), Color(0xFF8AA39B), Color(0xFFB9C9C2), Color(0xFF6C7774))

fun slotColor(p: SlotPart): Color = when (p.kind) {
    SlotKind.PLACE -> SnapColor.Green
    SlotKind.REFRESH -> SnapColor.Highlight
    SlotKind.BRAND -> BrandTones[p.brandIndex % BrandTones.size]
}

/** How the OS geofence slots are shared right now: one segment per group. */
@Composable
fun SlotMeter(total: Int, parts: List<SlotPart>, modifier: Modifier = Modifier) {
    val used = parts.sumOf { it.count }
    val free = (total - used).coerceAtLeast(0)
    val desc = stringResource(R.string.slot_meter_desc, total, used)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.M)) {
        Row(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(SnapColor.Surface).semantics { contentDescription = desc },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            parts.filter { it.count > 0 }.forEach { p -> Box(Modifier.weight(p.count.toFloat()).height(14.dp).background(slotColor(p))) }
            if (free > 0) Box(Modifier.weight(free.toFloat()).height(14.dp).background(SnapColor.Paper))
        }
        parts.forEach { p -> SlotRow(slotColor(p), p.label, p.count, muted = false) }
        SlotRow(SnapColor.Paper, stringResource(R.string.slot_empty), free, muted = true, outlined = true)
    }
}

@Composable
private fun SlotRow(swatch: Color, label: String, count: Int, muted: Boolean, outlined: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(swatch)
                .then(if (outlined) Modifier.border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(3.dp)) else Modifier),
        )
        Text(label, style = SnapType.Body.copy(color = if (muted) SnapColor.Muted else SnapColor.Ink), modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.slot_count, count),
            style = SnapType.Distance.copy(fontSize = 16.sp, lineHeight = 22.sp, color = if (muted) SnapColor.Muted else SnapColor.Ink),
        )
    }
}

/** A screen's big heading, with an optional small line beside it. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, summary: String? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = SnapType.Hero, modifier = Modifier.semantics { heading() })
        if (summary != null) Text(summary, style = SnapType.Meta)
    }
}

/** The common frame of a white card with a thin outline, used by the status screen. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SnapColor.Surface)
            .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(16.dp))
            .padding(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) { content() }
}

/** A label on the left and a bold value on the right. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(label, style = SnapType.Body)
        Text(value, style = SnapType.Body.copy(fontWeight = FontWeight.Bold))
    }
}
