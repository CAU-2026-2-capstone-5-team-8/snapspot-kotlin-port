package com.capstone.snapspot.data.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.capstone.snapspot.MainActivity
import com.capstone.snapspot.R
import com.capstone.snapspot.domain.Alert
import com.capstone.snapspot.domain.AlertKind
import com.capstone.snapspot.domain.AlertSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val CHANNEL_ID = "nearby-places"

/** The link a notification opens: a coupon opens its detail, a place opens the map on that place. */
fun alertUri(alert: Alert): Uri = when (alert.kind) {
    AlertKind.COUPON -> Uri.parse("snapspot://coupon/${Uri.encode(alert.targetId)}")
    AlertKind.PLACE -> Uri.parse("snapspot://place/${Uri.encode(alert.targetId)}")
}

/** Creates the notification channel. Safe to call on every start. */
fun createAlertChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 250, 250)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** Posts alerts as Android notifications. */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) : AlertSink {
    @SuppressLint("MissingPermission") // areNotificationsEnabled() is false without POST_NOTIFICATIONS
    override fun show(alert: Alert): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        createAlertChannel(context)
        val open = Intent(Intent.ACTION_VIEW, alertUri(alert), context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(
            context,
            alert.targetId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        // One notification per place or coupon: alerting again replaces the old one instead of stacking.
        manager.notify(alert.targetId.hashCode(), notification)
        return true
    }
}
