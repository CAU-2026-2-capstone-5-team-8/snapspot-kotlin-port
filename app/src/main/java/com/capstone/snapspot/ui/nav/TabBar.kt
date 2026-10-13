package com.capstone.snapspot.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capstone.snapspot.R
import com.capstone.snapspot.ui.components.topBorder
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapFont
import com.capstone.snapspot.ui.theme.Space

object Route {
    const val MAP = "map"
    const val COUPONS = "coupons"
    const val LIST = "list"
    const val STATUS = "status"
    const val ADD = "add"
    const val PERMISSIONS = "permissions"
    const val COUPON = "coupon/{id}"

    /** Where alerts lead: a coupon alert opens the coupon, a place alert opens the map on that place. */
    const val COUPON_LINK = "snapspot://coupon/{id}"
    const val PLACE_LINK = "snapspot://place/{placeId}"
    const val PLACE_ARG = "placeId"

    fun coupon(id: String) = "coupon/$id"

    val TABS = listOf(MAP, COUPONS, LIST, STATUS)
}

/** Map, coupons, a raised green plus for adding, places, status. */
@Composable
fun TabBar(current: String?, onSelect: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(SnapColor.Surface)
            .topBorder()
            .navigationBarsPadding()
            .padding(top = Space.S, bottom = Space.S),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabButton(Icons.Rounded.Map, stringResource(R.string.tab_map), current == Route.MAP) { onSelect(Route.MAP) }
        TabButton(Icons.Rounded.Sell, stringResource(R.string.tab_coupons), current == Route.COUPONS) { onSelect(Route.COUPONS) }
        AddButton(onAdd)
        TabButton(Icons.Rounded.Bookmark, stringResource(R.string.tab_list), current == Route.LIST) { onSelect(Route.LIST) }
        TabButton(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.tab_status), current == Route.STATUS) { onSelect(Route.STATUS) }
    }
}

@Composable
private fun TabButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .defaultMinSize(minWidth = 60.dp, minHeight = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) SnapColor.Ink else SnapColor.Muted, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = if (selected) SnapColor.Ink else SnapColor.Muted,
            fontFamily = SnapFont.Plex,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val description = stringResource(R.string.add_from_screenshot)
    Box(
        Modifier
            .offset(y = (-28).dp)
            .size(60.dp)
            .clip(CircleShape)
            .background(SnapColor.Green)
            .border(BorderStroke(4.dp, SnapColor.Surface), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = description, tint = SnapColor.Surface, modifier = Modifier.size(28.dp))
    }
}
