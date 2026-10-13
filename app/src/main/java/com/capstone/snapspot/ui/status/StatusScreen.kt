package com.capstone.snapspot.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.domain.MAX_REGIONS
import com.capstone.snapspot.domain.REFRESH_RADIUS_M
import com.capstone.snapspot.domain.ago
import com.capstone.snapspot.domain.brandKey
import com.capstone.snapspot.domain.model.Coupon
import com.capstone.snapspot.domain.model.WindowState
import com.capstone.snapspot.domain.slotParts
import com.capstone.snapspot.ui.components.ButtonKind
import com.capstone.snapspot.ui.components.Fact
import com.capstone.snapspot.ui.components.InfoCard
import com.capstone.snapspot.ui.components.Notice
import com.capstone.snapspot.ui.components.SlotMeter
import com.capstone.snapspot.ui.components.SnapButton
import com.capstone.snapspot.ui.components.WatchStatus
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space

/** Whether alerts will arrive, how the watch slots are shared, and the measurement log. */
@Composable
fun StatusContent(
    perms: AlertPermissions,
    win: WindowState?,
    coupons: List<Coupon>,
    logCount: Int,
    onOpenSettings: () -> Unit,
    onExportLog: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onOpenGuide: (() -> Unit)? = null,
) {
    val watching = win?.byGroup.orEmpty().values.sum()
    val brandNames = coupons.associate { brandKey(it.brand) to it.brand }
    Column(
        modifier
            .fillMaxSize()
            .background(SnapColor.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.L + 4.dp, vertical = Space.Xl),
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        Text(stringResource(R.string.status_title), style = SnapType.Hero, modifier = Modifier.semantics { heading() })
        WatchStatus(on = perms.alertsOn, watching = watching)

        if (perms.missingAny) {
            Notice(
                text = stringResource(R.string.status_perms_notice),
                action = stringResource(R.string.status_open_settings) to onOpenSettings,
            )
            if (onOpenGuide != null) {
                SnapButton(stringResource(R.string.status_open_guide), onOpenGuide, kind = ButtonKind.Primary, modifier = Modifier.fillMaxWidth())
            }
        }

        InfoCard {
            Text(stringResource(R.string.status_watch_title), style = SnapType.Title)
            Text(
                stringResource(R.string.status_watch_body, MAX_REGIONS, (REFRESH_RADIUS_M / 1000.0).let { if (it == Math.floor(it)) it.toInt().toString() else it.toString() }),
                style = SnapType.Meta,
            )
            SlotMeter(total = MAX_REGIONS, parts = slotParts(win, brandNames))
        }

        InfoCard {
            Text(stringResource(R.string.status_last_title), style = SnapType.Title)
            if (win != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    Fact(stringResource(R.string.fact_time), ago(win.at, now))
                    Fact(
                        stringResource(R.string.fact_kakao),
                        if (win.requests == 0) stringResource(R.string.fact_cached) else stringResource(R.string.fact_requests, win.requests),
                    )
                    Fact(
                        stringResource(R.string.fact_here),
                        stringResource(if (win.here != null) R.string.fact_here_known else R.string.fact_here_unknown),
                    )
                }
            } else {
                Text(stringResource(R.string.status_never), style = SnapType.Meta)
            }
        }

        InfoCard {
            Text(stringResource(R.string.log_title), style = SnapType.Title)
            Text(stringResource(R.string.log_body), style = SnapType.Meta)
            Fact(stringResource(R.string.log_saved), stringResource(R.string.log_count, logCount))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S), verticalAlignment = Alignment.CenterVertically) {
                SnapButton(stringResource(R.string.log_export), onExportLog, kind = ButtonKind.Secondary, enabled = logCount > 0)
                SnapButton(stringResource(R.string.log_clear), onClearLog, kind = ButtonKind.Quiet, enabled = logCount > 0)
            }
        }
    }
}
