package com.netblocker.pro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs

/**
 * 1x1 home-screen widget: a shield that flips protection on/off, exactly like
 * the Quick Settings tile. Pure background toggle — no UI is opened unless the
 * one-time VPN consent dialog is genuinely required.
 */
class ToggleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        WidgetHelper.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            WidgetHelper.toggleFromWidget(context)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.netblocker.pro.widget.TOGGLE"

        fun togglePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 20,
            Intent(context, ToggleWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun buildRemoteViews(context: Context, on: Boolean): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_toggle)
            rv.setInt(
                R.id.widgetToggleShield,
                "setColorFilter",
                ContextCompat.getColor(context, if (on) R.color.ios_green else R.color.ios_secondary)
            )
            rv.setContentDescription(R.id.widgetToggleShield, context.getString(
                if (on) R.string.tile_desc_on else R.string.tile_desc_off
            ))
            rv.setOnClickPendingIntent(R.id.widgetToggleRoot, togglePendingIntent(context))
            return rv
        }
    }
}
