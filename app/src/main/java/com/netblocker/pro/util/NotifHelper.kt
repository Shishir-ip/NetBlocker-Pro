package com.netblocker.pro.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.netblocker.pro.MainActivity
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine

object NotifHelper {

    const val CHANNEL_ID = "protection"
    private const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Firewall protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the network firewall is active"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun update(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shouldShow = Prefs.protectionOn(appContext) &&
            Prefs.showNotification(appContext) &&
            Engine.usesRootEngine(appContext)
        if (!shouldShow) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val intent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(appContext.getString(R.string.notification_title))
            .setContentText(appContext.getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
