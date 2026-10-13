package com.capstone.snapspot.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.AlertPermissions
import com.capstone.snapspot.domain.HowToAsk
import com.capstone.snapspot.domain.PermissionStep
import com.capstone.snapspot.domain.guideSteps
import com.capstone.snapspot.domain.howToAsk
import com.capstone.snapspot.domain.isGranted
import com.capstone.snapspot.domain.nextStep
import com.capstone.snapspot.ui.AppViewModel
import com.capstone.snapspot.ui.components.ButtonKind
import com.capstone.snapspot.ui.components.SnapButton
import com.capstone.snapspot.ui.theme.SnapColor
import com.capstone.snapspot.ui.theme.SnapType
import com.capstone.snapspot.ui.theme.Space
import kotlinx.coroutines.delay

/** How long "all set" stays up before the guide closes itself. */
private const val CLOSE_DELAY_MS = 900L

/**
 * Walks the user through what alerts need, one step at a time: location, notifications, "always" location, then
 * the phone's location switch and a battery hint. Pop-ups come from here, settings screens are opened for what a
 * pop-up cannot grant. It closes itself once everything required is on.
 */
@Composable
fun PermissionGuideRoute(app: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val perms by app.perms.collectAsStateWithLifecycle()
    val denied by app.deniedSteps.collectAsStateWithLifecycle()
    val device by app.deviceLocation.collectAsStateWithLifecycle()
    val batteryRestricted by app.batteryRestricted.collectAsStateWithLifecycle()
    val sdk = Build.VERSION.SDK_INT

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.onPermissionResult(PermissionStep.FOREGROUND_LOCATION)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.onPermissionResult(PermissionStep.NOTIFICATIONS)
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.onPermissionResult(PermissionStep.BACKGROUND_LOCATION)
    }
    val switchOn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        app.refreshEnvironment()
    }

    LaunchedEffect(Unit) { app.refreshEnvironment() }

    val ready = nextStep(perms, sdk) == PermissionStep.DONE && device.on
    // Opened with everything already on: stay and say so. Otherwise close by itself once it gets there.
    var sawIncomplete by remember { mutableStateOf(!ready) }
    LaunchedEffect(ready) {
        if (!ready) {
            sawIncomplete = true
        } else if (sawIncomplete) {
            delay(CLOSE_DELAY_MS)
            onClose()
        }
    }

    PermissionGuideContent(
        perms = perms,
        sdk = sdk,
        denied = denied,
        deviceLocationOn = device.on,
        batteryRestricted = batteryRestricted,
        onAsk = { step, how ->
            when (how) {
                HowToAsk.SETTINGS ->
                    if (step == PermissionStep.NOTIFICATIONS) context.openNotificationSettings() else context.openAppSettings()
                HowToAsk.DIALOG -> when (step) {
                    PermissionStep.FOREGROUND_LOCATION ->
                        foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    PermissionStep.NOTIFICATIONS -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    PermissionStep.BACKGROUND_LOCATION -> background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    PermissionStep.DONE -> Unit
                }
            }
        },
        accuracyLow = device.accuracyLow,
        onFixDeviceLocation = context::openLocationSettings,
        onImproveAccuracy = { device.accuracyFix?.let { switchOn.launch(IntentSenderRequest.Builder(it).build()) } },
        onOpenBatterySettings = context::openBatterySettings,
        onClose = onClose,
    )
}

/** The guide without any Android wiring, so its states can be shown and tested directly. */
@Composable
fun PermissionGuideContent(
    perms: AlertPermissions,
    sdk: Int,
    denied: Set<PermissionStep>,
    deviceLocationOn: Boolean,
    batteryRestricted: Boolean,
    accuracyLow: Boolean,
    onAsk: (PermissionStep, HowToAsk) -> Unit,
    onFixDeviceLocation: () -> Unit,
    onImproveAccuracy: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = nextStep(perms, sdk)
    val ready = current == PermissionStep.DONE && deviceLocationOn
    Column(
        modifier
            .fillMaxSize()
            .background(SnapColor.Paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.L + 4.dp, vertical = Space.Xl),
        verticalArrangement = Arrangement.spacedBy(Space.L),
    ) {
        Text(stringResource(R.string.perm_guide_title), style = SnapType.Hero, modifier = Modifier.semantics { heading() })
        if (ready) {
            Text(stringResource(R.string.perm_all_set_title), style = SnapType.Title)
            Text(stringResource(R.string.perm_all_set_body), style = SnapType.Body)
        } else {
            Text(stringResource(R.string.perm_guide_intro), style = SnapType.Body)
        }

        for (step in guideSteps(sdk)) {
            val done = isGranted(step, perms)
            val how = howToAsk(step, sdk, denied = step in denied)
            StepCard(
                title = stringResource(titleOf(step)),
                why = stringResource(whyOf(step)),
                done = done,
                current = step == current,
                howHint = if (how == HowToAsk.SETTINGS) howOf(step)?.let { stringResource(it) } else null,
                deniedHint = step in denied && !done,
                button = stringResource(if (how == HowToAsk.DIALOG) R.string.perm_ask_dialog else R.string.perm_ask_settings),
                onClick = { onAsk(step, how) },
            )
        }

        if (!deviceLocationOn) {
            StepCard(
                title = stringResource(R.string.perm_device_location_title),
                why = stringResource(R.string.perm_device_location_why),
                done = false,
                current = current == PermissionStep.DONE,
                button = stringResource(R.string.perm_device_location_button),
                onClick = onFixDeviceLocation,
            )
        }
        // Recommended only: alerts work without it, so it never keeps the guide open.
        if (accuracyLow && deviceLocationOn) {
            StepCard(
                title = stringResource(R.string.perm_accuracy_title),
                why = stringResource(R.string.perm_accuracy_why),
                done = false,
                current = false,
                button = stringResource(R.string.perm_accuracy_button),
                onClick = onImproveAccuracy,
                optional = true,
            )
        }
        if (batteryRestricted) {
            StepCard(
                title = stringResource(R.string.perm_battery_title),
                why = stringResource(R.string.perm_battery_why),
                done = false,
                current = false,
                button = stringResource(R.string.perm_battery_button),
                onClick = onOpenBatterySettings,
                optional = true,
            )
        }

        SnapButton(
            label = stringResource(if (ready) R.string.perm_close else R.string.perm_later),
            onClick = onClose,
            kind = if (ready) ButtonKind.Primary else ButtonKind.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun titleOf(step: PermissionStep): Int = when (step) {
    PermissionStep.FOREGROUND_LOCATION -> R.string.perm_step_fg_title
    PermissionStep.NOTIFICATIONS -> R.string.perm_step_notif_title
    PermissionStep.BACKGROUND_LOCATION -> R.string.perm_step_bg_title
    PermissionStep.DONE -> R.string.perm_step_done
}

private fun whyOf(step: PermissionStep): Int = when (step) {
    PermissionStep.FOREGROUND_LOCATION -> R.string.perm_step_fg_why
    PermissionStep.NOTIFICATIONS -> R.string.perm_step_notif_why
    PermissionStep.BACKGROUND_LOCATION -> R.string.perm_step_bg_why
    PermissionStep.DONE -> R.string.perm_all_set_body
}

/** Where to tap in settings, for the steps that end up there. */
private fun howOf(step: PermissionStep): Int? = when (step) {
    PermissionStep.NOTIFICATIONS -> R.string.perm_step_notif_how
    PermissionStep.BACKGROUND_LOCATION -> R.string.perm_step_bg_how
    else -> null
}

@Composable
private fun StepCard(
    title: String,
    why: String,
    done: Boolean,
    current: Boolean,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    howHint: String? = null,
    deniedHint: Boolean = false,
    optional: Boolean = false,
) {
    val outline = if (current && !done) SnapColor.Green else SnapColor.Line
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (done) SnapColor.GreenSoft else SnapColor.Surface)
            .border(BorderStroke(if (current && !done) 2.dp else 1.dp, outline), RoundedCornerShape(16.dp))
            .padding(Space.L),
        verticalArrangement = Arrangement.spacedBy(Space.S),
    ) {
        val state = when {
            done -> stringResource(R.string.perm_step_done)
            current -> stringResource(R.string.perm_step_current)
            else -> null
        }
        if (state != null) Text(state, style = SnapType.Label.copy(color = if (done) SnapColor.Green else SnapColor.Amber))
        Text(title, style = SnapType.Title)
        Text(why, style = SnapType.Meta)
        if (!done) {
            if (howHint != null) Text(howHint, style = SnapType.Body)
            if (deniedHint) Text(stringResource(R.string.perm_denied_hint), style = SnapType.Meta.copy(color = SnapColor.Amber))
            // Only the step to do now is a button; the rest wait their turn, except optional notes.
            if (current || optional) {
                SnapButton(
                    label = button,
                    onClick = onClick,
                    kind = if (optional) ButtonKind.Secondary else ButtonKind.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
