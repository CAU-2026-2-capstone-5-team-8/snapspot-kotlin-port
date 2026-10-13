package com.capstone.snapspot.data.permissions

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the system may hold this app back to save battery. It is only a hint: geofence alerts come from Google
 * Play services and usually still arrive, but some phones stop apps they consider idle. The guide never asks for
 * an exemption itself; it only points at the system screen.
 */
@Singleton
class BatteryHint @Inject constructor(@ApplicationContext private val context: Context) {
    fun isRestricted(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
