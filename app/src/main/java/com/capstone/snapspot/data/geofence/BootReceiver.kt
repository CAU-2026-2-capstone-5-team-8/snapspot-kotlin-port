package com.capstone.snapspot.data.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android forgets geofences when the phone restarts (and may when the app is updated); plan and register again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            enqueueReplan(context, fallbackToSaved = true)
        }
    }
}
