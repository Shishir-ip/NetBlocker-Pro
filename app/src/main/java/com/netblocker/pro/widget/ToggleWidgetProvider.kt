package com.netblocker.pro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.ui.WidgetToggleActivity

/**
 * Small (2x1) home-screen widget: tap anywhere to flip protection on/off.
 */
class ToggleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        WidgetHelper.updateAll(context)
    }

    companion object {
        fun buildRemoteViews(context: Context, on: Boolean): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_toggle)
            rv.setTextViewText(R.id.widgetToggleTitle, context.getString(R.string.app_name))
            rv.setTextViewText(
                R.id.widgetToggleState,
                context.getString(if (on) R.string.widget_on else R.string.widget_off)
            )
            rv.setTextColor(
                R.id.widgetToggleState,
                ContextCompat.getColor(context, if (on) R.color.ios_green else R.color.ios_secondary)
            )
            rv.setInt(
                R.id.widgetToggleShield,
                "setColorFilter",
                ContextCompat.getColor(context, if (on) R.color.ios_green else R.color.ios_secondary)
            )
            rv.setOnClickPendingIntent(R.id.widgetToggleRoot, WidgetToggleActivity.pendingIntent(context))
            return rv
        }
    }
}
