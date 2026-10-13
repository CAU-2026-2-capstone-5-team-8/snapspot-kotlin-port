package com.capstone.snapspot.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // A phone without that screen: nothing more to do.
    }
}

/** This app's page in system settings, where permissions can be changed. */
fun Context.openAppSettings() =
    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

fun Context.openNotificationSettings() =
    openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

/** The phone-wide location switch. */
fun Context.openLocationSettings() = openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

/** The list of apps with battery restrictions; the user decides there, the app asks for nothing. */
fun Context.openBatterySettings() = openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
