package com.capstone.snapspot.ui.coupons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.BRANCH_RADIUS_M
import com.capstone.snapspot.domain.PLACE_GROUP
import com.capstone.snapspot.domain.daysLeft
import com.capstone.snapspot.domain.expiresLabel
import com.capstone.snapspot.domain.isNear
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.domain.nearestBranch
import com.capstone.snapspot.domain.splitCoupons
import com.capstone.snapspot.ui.components.EmptyBox
import com.capstone.snapspot.ui.components.ScreenHeader
import com.capstone.snapspot.ui.components.TicketCard
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/** The coupon wallet: active coupons soonest first, then the ones that are used or expired. */
@Composable
fun CouponsContent(
    coupons: List<Coupon>,
    win: WindowState?,
    here: Coords?,
    onOpen: (Coupon) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val (active, past) = splitCoupons(coupons, now)
    val branchSlots = win?.byGroup.orEmpty().filterKeys { it != PLACE_GROUP }.values.sum()
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
            title = stringResource(R.string.coupons_title),
            summary = when {
                active.isEmpty() -> null
                branchSlots > 0 -> stringResource(R.string.coupons_count_watching, active.size, branchSlots)
                else -> stringResource(R.string.coupons_count, active.size)
            },
        )

        if (active.isEmpty()) {
            EmptyBox(stringResource(R.string.coupons_empty_title), stringResource(R.string.coupons_empty_body))
        } else {
            active.forEach { c ->
                val nearest = nearestBranch(c, win, here)
                TicketCard(
                    brand = c.brand,
                    benefit = c.benefit,
                    imageUri = c.imageUri,
                    expiresLabel = expiresLabel(c),
                    days = daysLeft(c, now),
                    nearest = nearest,
                    near = isNear(nearest?.distance, BRANCH_RADIUS_M),
                    onClick = { onOpen(c) },
                )
            }
        }

        if (past.isNotEmpty()) {
            Text(stringResource(R.string.past_coupons), style = SnapType.Title, modifier = Modifier.padding(top = Space.L))
            past.forEach { c ->
                TicketCard(
                    brand = c.brand,
                    benefit = c.benefit,
                    imageUri = c.imageUri,
                    expiresLabel = if (c.usedAt != null) stringResource(R.string.used_label) else expiresLabel(c),
                    days = null,
                    nearest = null,
                    near = false,
                    dimmed = true,
                    onClick = { onOpen(c) },
                )
            }
        }
    }
}
