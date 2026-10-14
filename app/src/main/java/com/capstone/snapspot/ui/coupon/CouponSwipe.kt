package com.capstone.snapspot.ui.coupon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.swipeGroup
import com.capstone.snapspot.ui.theme.SnapColor

/**
 * The coupon screen for [id], and when the same brand has other active coupons, a swipe left and right to them
 * (soonest-expiring first, starting at [id]). [content] draws one coupon and is null when [id] matches none.
 * With nothing to swipe to, it is just [content]: the screen looks and works as it did.
 */
@Composable
fun CouponSwipe(
    coupons: List<Coupon>,
    id: String?,
    modifier: Modifier = Modifier,
    content: @Composable (Coupon?) -> Unit,
) {
    val current = coupons.firstOrNull { it.id == id }
    if (current == null) {
        content(null)
        return
    }
    val now = remember(id) { System.currentTimeMillis() }
    val group = swipeGroup(coupons, current, now)
    if (group.size < 2) {
        content(current)
        return
    }

    val pager = rememberPagerState(initialPage = group.indexOfFirst { it.id == current.id }.coerceAtLeast(0)) { group.size }
    Box(modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { group.getOrNull(it)?.id ?: it }) { page ->
            content(group.getOrNull(page))
        }
        // In the gap above the screen's own header, so it never covers the brand name or the close button.
        val position = stringResource(R.string.coupon_swipe_position, group.size, pager.currentPage + 1)
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 3.dp)
                .clearAndSetSemantics { contentDescription = position },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(group.size) { i ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) SnapColor.Green else SnapColor.Line),
                )
            }
        }
    }
}
