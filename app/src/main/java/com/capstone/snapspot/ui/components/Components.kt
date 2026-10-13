package com.capstone.snapspot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.model.Candidate
import com.capstone.snapspot.domain.model.CandidateSource
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapFont
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space
import java.io.File
import kotlin.math.roundToInt

enum class ButtonKind { Primary, Secondary, Quiet }

/** A single line along the top edge, like the dividers between list rows. */
fun Modifier.topBorder(color: Color = SnapColor.Line, width: androidx.compose.ui.unit.Dp = 1.dp): Modifier =
    drawBehind {
        val w = width.toPx()
        drawLine(color, Offset(0f, w / 2), Offset(size.width, w / 2), strokeWidth = w)
    }

@Composable
fun SnapButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val off = !enabled || busy
    val background = when (kind) {
        ButtonKind.Primary -> SnapColor.Green
        ButtonKind.Secondary -> SnapColor.GreenSoft
        ButtonKind.Quiet -> Color.Transparent
    }
    val textColor = when (kind) {
        ButtonKind.Primary -> SnapColor.Surface
        ButtonKind.Secondary -> SnapColor.Green
        ButtonKind.Quiet -> SnapColor.Muted
    }
    val minHeight = when (kind) {
        ButtonKind.Primary -> 52.dp
        ButtonKind.Secondary -> 44.dp
        ButtonKind.Quiet -> 36.dp
    }
    Row(
        modifier = modifier
            .alpha(if (off) 0.5f else 1f)
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = !off, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.Primary) Space.Xl else Space.L),
        horizontalArrangement = Arrangement.spacedBy(Space.S, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = textColor, strokeWidth = 2.dp)
        Text(
            label,
            color = textColor,
            fontFamily = SnapFont.Plex,
            fontWeight = if (kind == ButtonKind.Quiet) FontWeight.Medium else FontWeight.Bold,
            fontSize = if (kind == ButtonKind.Primary) 16.sp else 15.sp,
        )
    }
}

@Composable
fun SnapField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    large: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
        Text(label, style = SnapType.Label)
        val style = if (large) {
            SnapType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
        } else {
            SnapType.Body
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style,
            cursorBrush = SolidColor(SnapColor.Green),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SnapColor.Surface)
                        .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(10.dp))
                        .padding(horizontal = Space.M, vertical = 10.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = SnapColor.Muted))
                    inner()
                }
            },
        )
    }
}

/** Amber note, used for anything that stops the app from working as expected. [action] is an optional fix-it button. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SnapColor.AmberSoft)
            .padding(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.M),
    ) {
        Text(text, style = SnapType.Body.copy(fontSize = 14.sp, color = SnapColor.Amber))
        if (action != null) {
            SnapButton(action.first, action.second, kind = ButtonKind.Secondary)
        }
    }
}

/** What an image field can show: a content URI, or a file in app storage. */
fun imageModel(uri: String): Any = if (uri.startsWith("/")) File(uri) else uri

/** The screenshot a place came from. It is how people recognize their own saves. */
@Composable
fun Shot(uri: String?, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(10.dp))
            .background(SnapColor.GreenSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(model = imageModel(uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("📍", fontSize = 22.sp)
        }
    }
}

/** The result of the map search for a place: sure, unsure, device geocoder only, or nothing. */
@Composable
fun MatchBox(matched: Candidate?, hasCoords: Boolean, modifier: Modifier = Modifier) {
    if (!hasCoords) {
        MatchCard(modifier, warn = true) {
            Text(stringResource(R.string.match_not_found_title), style = headStyle(SnapColor.Amber))
            Text(stringResource(R.string.match_not_found_body), style = SnapType.Meta.copy(color = SnapColor.Amber))
        }
        return
    }
    if (matched == null) {
        MatchCard(modifier, warn = false) {
            Text(stringResource(R.string.match_device_title), style = headStyle(SnapColor.Ink))
            Text(stringResource(R.string.match_device_body), style = SnapType.Meta)
        }
        return
    }
    val sure = matched.score >= SURE_SCORE
    val tone = if (sure) SnapColor.Ink else SnapColor.Amber
    MatchCard(modifier, warn = !sure) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.Bottom) {
            Text(
                if (matched.source == CandidateSource.ADDRESS) stringResource(R.string.match_address_title) else matched.name,
                style = headStyle(tone),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${percent(matched.score)}%", style = SnapType.Distance.copy(fontSize = 20.sp, color = if (sure) SnapColor.Green else SnapColor.Amber))
        }
        Text(matched.address, style = SnapType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(if (sure) R.string.match_sure else R.string.match_unsure),
            style = SnapType.Meta.copy(color = if (sure) SnapColor.Green else SnapColor.Amber),
        )
    }
}

/** A match at or above this share of certainty is shown as found, below it as a guess. */
const val SURE_SCORE = 0.7

fun percent(score: Double): Int = (score * 100).roundToInt()

private fun headStyle(color: Color) = SnapType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, color = color)

@Composable
private fun MatchCard(modifier: Modifier, warn: Boolean, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (warn) SnapColor.AmberSoft else SnapColor.GreenSoft)
            .padding(Space.M),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
fun CandidateRow(c: Candidate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = if (c.source == CandidateSource.ADDRESS) stringResource(R.string.match_address_title) else c.name
    val description = stringResource(R.string.candidate_description, c.name, percent(c.score))
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .topBorder()
            .padding(horizontal = Space.M, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SnapType.Body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.address, style = SnapType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${percent(c.score)}%", style = SnapType.Meta.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
    }
}

@Composable
fun <T> Segmented(
    options: List<T>,
    value: T,
    onChange: (T) -> Unit,
    format: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SnapColor.Paper)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val on = option == value
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (on) Modifier.background(SnapColor.Surface).border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(9.dp)) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onChange(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    format(option),
                    color = if (on) SnapColor.Ink else SnapColor.Muted,
                    fontFamily = SnapFont.Plex,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
