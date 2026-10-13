package com.capstone.snapspot.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.capstone.snapspot.domain.AlertPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads which alert permissions are granted right now. It never asks; asking is a separate flow. */
@Singleton
class PermissionReader @Inject constructor(@ApplicationContext private val context: Context) {

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun read(): AlertPermissions {
        val fg = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        // Before Android 10 there is no separate background permission.
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else fg
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return AlertPermissions(notif = notif, fg = fg, bg = bg)
    }
}
