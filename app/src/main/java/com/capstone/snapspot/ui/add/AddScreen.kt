package com.capstone.snapspot.ui.add

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.distanceMeters
import com.capstone.snapspot.domain.draft.Draft
import com.capstone.snapspot.domain.draft.RADII
import com.capstone.snapspot.domain.draft.Stage
import com.capstone.snapspot.domain.formatDistance
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Kind
import com.capstone.snapspot.ui.components.ButtonKind
import com.capstone.snapspot.ui.components.CandidateRow
import com.capstone.snapspot.ui.components.MatchBox
import com.capstone.snapspot.ui.components.Notice
import com.capstone.snapspot.ui.components.Segmented
import com.capstone.snapspot.ui.components.Shot
import com.capstone.snapspot.ui.components.SnapButton
import com.capstone.snapspot.ui.components.SnapField
import com.capstone.snapspot.ui.components.imageModel
import com.capstone.snapspot.ui.components.topBorder
import com.capstone.snapspot.ui.theme.LocalReduceMotion
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapFont
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/** Everything the add screen can ask for. State lives with the caller, so the screen is easy to test. */
class AddActions(
    val onClose: () -> Unit,
    val onSave: () -> Unit,
    val onKind: (Kind) -> Unit,
    val onChange: ((Draft) -> Draft) -> Unit,
    val onRefindAddress: () -> Unit,
    val onPinHere: () -> Unit,
    val onPickCandidate: (Candidate) -> Unit,
    val onRefreshBranches: () -> Unit,
)

private val SidePadding = Space.L + 4.dp

@Composable
fun AddContent(draft: Draft, here: Coords?, busy: Boolean, actions: AddActions, modifier: Modifier = Modifier) {
    val isCoupon = draft.kind == Kind.COUPON
    val ready = draft.stage == Stage.READY
    Column(modifier.fillMaxSize().background(SnapColor.Paper).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SidePadding, vertical = Space.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    when {
                        !ready -> R.string.add_title_reading
                        isCoupon -> R.string.add_title_coupon
                        else -> R.string.add_title_place
                    },
                ),
                style = SnapType.Title,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // 48dp touch target around the 40dp circle.
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = actions.onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(SnapColor.Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = SnapColor.Ink, modifier = Modifier.size(22.dp))
                }
            }
        }

        if (!ready) {
            ReadingContent(draft)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SidePadding).padding(bottom = Space.Xl),
            verticalArrangement = Arrangement.spacedBy(SidePadding),
        ) {
            Segmented(
                options = listOf(Kind.PLACE, Kind.COUPON),
                value = draft.kind,
                onChange = actions.onKind,
                format = { stringResource(if (it == Kind.PLACE) R.string.kind_place else R.string.kind_coupon) },
            )
            if (isCoupon) CouponForm(draft, here, busy, actions) else PlaceForm(draft, here, busy, actions)
        }
        HorizontalDivider(color = SnapColor.Line)
        Box(Modifier.fillMaxWidth().background(SnapColor.Paper).padding(horizontal = SidePadding, vertical = Space.M)) {
            SnapButton(
                label = stringResource(if (isCoupon) R.string.save_coupon else R.string.save_place),
                onClick = actions.onSave,
                enabled = if (isCoupon) draft.brand.isNotBlank() else draft.coords != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaceForm(draft: Draft, here: Coords?, busy: Boolean, a: AddActions) {
    if (!draft.isPlace) Notice(stringResource(R.string.notice_not_place))

    Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.Top) {
        Shot(draft.imageUri, 96.dp, 128.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            SnapField(stringResource(R.string.field_place_name), draft.name, { v -> a.onChange { it.copy(name = v) } }, large = true)
            SnapField(stringResource(R.string.field_category), draft.category, { v -> a.onChange { it.copy(category = v) } })
        }
    }
    SnapField(stringResource(R.string.field_note), draft.note, { v -> a.onChange { it.copy(note = v) } })
    SnapField(stringResource(R.string.field_address), draft.address, { v -> a.onChange { it.copy(address = v) } })
    SnapField(
        stringResource(R.string.field_end_date_place), draft.endDate, { v -> a.onChange { it.copy(endDate = v) } },
        placeholder = stringResource(R.string.hint_end_date_place),
    )

    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.label_found_location), style = SnapType.Label)
        MatchBox(draft.matched, hasCoords = draft.coords != null)
        if (draft.coords != null && here != null) {
            Text(stringResource(R.string.distance_from_here, formatDistance(distanceMeters(here, draft.coords))), style = SnapType.Meta)
        }
        val others = draft.candidates.filter { it != draft.matched }.take(3)
        if (others.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SnapColor.Surface)
                    .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(12.dp)),
            ) {
                Text(stringResource(R.string.other_candidates), style = SnapType.Label, modifier = Modifier.padding(horizontal = Space.M, vertical = Space.S))
                others.forEach { c -> CandidateRow(c, onClick = { a.onPickCandidate(c) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S)) {
            SnapButton(stringResource(R.string.btn_refind_address), a.onRefindAddress, Modifier.weight(1f), ButtonKind.Secondary, busy = busy)
            SnapButton(stringResource(R.string.btn_pin_here), a.onPinHere, Modifier.weight(1f), ButtonKind.Secondary)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.label_radius), style = SnapType.Label)
        Segmented(RADII, draft.radius, { r -> a.onChange { it.copy(radius = r) } }, format = { "${it}m" })
    }
}

/** Coupon fields plus a preview of the branches that will trigger alerts. */
@Composable
private fun CouponForm(draft: Draft, here: Coords?, busy: Boolean, a: AddActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.Top) {
        Shot(draft.imageUri, 96.dp, 128.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.M)) {
            SnapField(stringResource(R.string.field_brand), draft.brand, { v -> a.onChange { it.copy(brand = v) } }, large = true)
            SnapField(stringResource(R.string.field_benefit), draft.benefit, { v -> a.onChange { it.copy(benefit = v) } })
        }
    }
    SnapField(
        stringResource(R.string.field_end_date_coupon), draft.endDate, { v -> a.onChange { it.copy(endDate = v) } },
        placeholder = stringResource(R.string.hint_end_date_coupon),
    )

    val branches = draft.branches
    Column(verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Text(stringResource(R.string.label_branches), style = SnapType.Label)
        when {
            here == null -> PreviewCard(
                warn = true,
                title = stringResource(R.string.preview_unknown_here_title),
                body = stringResource(R.string.preview_unknown_here_body),
            )
            branches == null -> PreviewCard(warn = false, title = stringResource(R.string.preview_searching))
            branches.isEmpty() -> PreviewCard(
                warn = true,
                title = stringResource(R.string.preview_none_title, draft.brand.ifEmpty { stringResource(R.string.preview_none_default_brand) }),
                body = stringResource(R.string.preview_none_body),
            )
            else -> PreviewCard(
                warn = false,
                title = stringResource(R.string.preview_count, draft.brand, branches.size),
                trailing = formatDistance(distanceMeters(here, Coords(branches[0].latitude, branches[0].longitude))),
                body = stringResource(R.string.preview_nearest, branches[0].name),
            )
        }
        if (here != null && !branches.isNullOrEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SnapColor.Surface)
                    .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(12.dp)),
            ) {
                branches.take(3).forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().topBorder().padding(horizontal = Space.M, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.M),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(b.name, style = SnapType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatDistance(distanceMeters(here, Coords(b.latitude, b.longitude))), style = SnapType.Meta)
                    }
                }
            }
        }
        SnapButton(stringResource(R.string.btn_refind_brand), a.onRefreshBranches, Modifier.fillMaxWidth(), ButtonKind.Secondary, busy = busy)
    }
}

@Composable
private fun PreviewCard(warn: Boolean, title: String, body: String? = null, trailing: String? = null) {
    val tone = if (warn) SnapColor.Amber else SnapColor.Ink
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (warn) SnapColor.AmberSoft else SnapColor.GreenSoft).padding(Space.M),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = SnapType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = tone),
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) Text(trailing, style = SnapType.Distance.copy(fontSize = 20.sp, color = SnapColor.Green))
        }
        if (body != null) Text(body, style = SnapType.Meta.copy(color = if (warn) SnapColor.Amber else SnapColor.Muted))
    }
}

/** The screenshot, with a highlighter sweeping over it while it is being read. */
@Composable
private fun ReadingContent(draft: Draft) {
    val reduceMotion = LocalReduceMotion.current
    var frameHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val barHeight = 40.dp
    val sweep = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "highlighter")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        ).value
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.Xl).padding(top = Space.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.Xl),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(SnapColor.GreenSoft)
                .border(BorderStroke(4.dp, SnapColor.Surface), RoundedCornerShape(18.dp))
                .onSizeChanged { frameHeightPx = it.height },
        ) {
            AsyncImage(model = imageModel(draft.imageUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (frameHeightPx > 0 && !reduceMotion) {
                val barPx = with(density) { barHeight.roundToPx() }
                val y = (-barPx + (frameHeightPx + barPx) * sweep).toInt()
                Box(
                    Modifier.fillMaxWidth().height(barHeight)
                        .offset { androidx.compose.ui.unit.IntOffset(0, y) }
                        .background(SnapColor.Highlight.copy(alpha = 0.55f)),
                )
            }
        }

        val reading = draft.stage == Stage.READING
        val steps = listOf(
            Step(stringResource(R.string.step_read), if (reading) StepState.NOW else StepState.DONE),
            Step(
                stringResource(if (draft.kind == Kind.COUPON) R.string.step_find_branches else R.string.step_find_place),
                if (draft.stage == Stage.LOCATING) StepState.NOW else StepState.TODO,
            ),
        )
        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Space.M),
        ) {
            steps.forEach { StepRow(it) }
            if (draft.name.isNotEmpty()) Text(stringResource(R.string.found_name, draft.name), style = SnapType.Meta)
        }
    }
}

private enum class StepState { DONE, NOW, TODO }

private data class Step(val label: String, val state: StepState)

@Composable
private fun StepRow(step: Step) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.M), verticalAlignment = Alignment.CenterVertically) {
        val border = if (step.state == StepState.TODO) SnapColor.Line else SnapColor.Green
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .then(if (step.state == StepState.DONE) Modifier.background(SnapColor.Green) else Modifier)
                .border(BorderStroke(2.dp, border), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (step.state == StepState.DONE) Icon(Icons.Rounded.Check, contentDescription = null, tint = SnapColor.Surface, modifier = Modifier.size(12.dp))
        }
        Text(
            step.label,
            style = SnapType.Body.copy(
                color = if (step.state == StepState.TODO) SnapColor.Muted else SnapColor.Ink,
                fontWeight = if (step.state == StepState.NOW) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
