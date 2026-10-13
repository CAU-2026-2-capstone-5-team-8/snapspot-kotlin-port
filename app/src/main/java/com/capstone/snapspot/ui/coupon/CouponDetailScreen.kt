package com.capstone.snapspot.ui.coupon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.BRANCH_RADIUS_M
import com.capstone.snapspot.domain.branchesOf
import com.capstone.snapspot.domain.dDay
import com.capstone.snapspot.domain.daysLeft
import com.capstone.snapspot.domain.expiresLabel
import com.capstone.snapspot.domain.formatDistance
import com.capstone.snapspot.domain.isActive
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.ui.components.ButtonKind
import com.capstone.snapspot.ui.components.SnapButton
import com.capstone.snapspot.ui.components.imageModel
import com.capstone.snapspot.ui.components.topBorder
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/**
 * One coupon, sized for the counter: the screenshot (with its barcode) as large as the screen allows.
 * [coupon] is null when the id does not match any saved coupon.
 */
@Composable
fun CouponDetailContent(
    coupon: Coupon?,
    win: WindowState?,
    here: Coords?,
    onClose: () -> Unit,
    onDelete: (Coupon) -> Unit,
    onUsed: (Coupon) -> Unit,
    onToCoupons: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onTest: ((Coupon) -> Unit)? = null,
) {
    if (coupon == null) {
        Column(
            modifier.fillMaxSize().background(SnapColor.Paper).safeDrawingPadding().padding(Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.M, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.coupon_missing_title), style = SnapType.Title)
            Text(stringResource(R.string.coupon_missing_body), style = SnapType.Meta)
            SnapButton(stringResource(R.string.to_coupons), onToCoupons, kind = ButtonKind.Secondary)
        }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    val active = isActive(coupon, now)
    val branches = if (active) branchesOf(coupon, win, here, limit = 3) else emptyList()
    val days = daysLeft(coupon, now)

    Column(modifier.fillMaxSize().background(SnapColor.Paper).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.L + 4.dp, vertical = Space.M),
            horizontalArrangement = Arrangement.spacedBy(Space.M),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(coupon.brand, style = SnapType.Hero.copy(fontSize = 28.sp, lineHeight = 34.sp), modifier = Modifier.semantics { heading() })
                Text(coupon.benefit, style = SnapType.Body.copy(fontWeight = FontWeight.Medium))
            }
            // 48dp touch target around the 40dp circle.
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(SnapColor.Surface), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = SnapColor.Ink, modifier = Modifier.size(22.dp))
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.L + 4.dp).padding(bottom = Space.Xl),
            verticalArrangement = Arrangement.spacedBy(Space.L),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SnapColor.Surface)
                    .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(16.dp))
                    .padding(Space.M),
                contentAlignment = Alignment.Center,
            ) {
                if (coupon.imageUri != null) {
                    val desc = stringResource(R.string.coupon_image_desc, coupon.brand)
                    AsyncImage(
                        model = imageModel(coupon.imageUri),
                        contentDescription = desc,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
                    )
                } else {
                    Text(stringResource(R.string.coupon_no_image), style = SnapType.Meta)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    when {
                        coupon.usedAt != null -> stringResource(R.string.coupon_used)
                        else -> expiresLabel(coupon) ?: stringResource(R.string.coupon_no_expiry)
                    },
                    style = SnapType.Body,
                )
                if (active) dDay(days)?.let { Text(it, style = SnapType.Distance.copy(fontSize = 22.sp, lineHeight = 26.sp, color = SnapColor.Green)) }
            }

            if (branches.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SnapColor.Surface)
                        .border(BorderStroke(1.dp, SnapColor.Line), RoundedCornerShape(12.dp)),
                ) {
                    Text(stringResource(R.string.nearby_branches), style = SnapType.Label, modifier = Modifier.padding(horizontal = Space.M, vertical = Space.S))
                    branches.forEach { b ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (isNear(b.distance, BRANCH_RADIUS_M)) Modifier.background(SnapColor.Highlight) else Modifier)
                                .topBorder()
                                .padding(horizontal = Space.M, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Space.M),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(b.name, style = SnapType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatDistance(b.distance), style = SnapType.Distance.copy(fontSize = 16.sp, lineHeight = 22.sp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = SnapColor.Line)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.L + 4.dp, vertical = Space.M),
            horizontalArrangement = Arrangement.spacedBy(Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                SnapButton(stringResource(R.string.btn_delete), { confirmDelete = true }, kind = ButtonKind.Quiet)
                if (onTest != null) SnapButton(stringResource(R.string.btn_test_alert), { onTest(coupon) }, kind = ButtonKind.Secondary)
                SnapButton(
                    stringResource(R.string.btn_used),
                    { onUsed(coupon) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                SnapButton(
                    stringResource(R.string.btn_delete_coupon),
                    { confirmDelete = true },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_coupon_title, coupon.brand)) },
            text = { Text(stringResource(R.string.delete_coupon_body)) },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.btn_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(coupon)
                }) { Text(stringResource(R.string.btn_delete)) }
            },
        )
    }
}
