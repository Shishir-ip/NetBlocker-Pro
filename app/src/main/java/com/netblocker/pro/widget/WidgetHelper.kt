package com.netblocker.pro.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs

/**
 * Keeps every home-screen widget in sync with the firewall state and pushes
 * data-set refreshes to the blocked-apps list widget.
 */
object WidgetHelper {

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appContext) ?: return
        val on = Prefs.protectionOn(appContext)
        val count = Prefs.getRules(appContext).size

        for (id in mgr.getAppWidgetIds(ComponentName(appContext, ToggleWidgetProvider::class.java))) {
            mgr.updateAppWidget(id, ToggleWidgetProvider.buildRemoteViews(appContext, on))
        }
        for (id in mgr.getAppWidgetIds(ComponentName(appContext, BlockListWidgetProvider::class.java))) {
            mgr.updateAppWidget(id, BlockListWidgetProvider.buildRemoteViews(appContext, on, count))
        }
    }

    fun notifyDataChanged(context: Context) {
        updateAll(context)
        val appContext = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appContext) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(appContext, BlockListWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }
    }
}

internal fun Drawable.toBitmap(): Bitmap? {
    if (this is android.graphics.drawable.BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    return try {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (e: Exception) {
        null
    }
}
