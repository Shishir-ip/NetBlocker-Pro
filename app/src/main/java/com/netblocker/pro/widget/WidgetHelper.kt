package com.netblocker.pro.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import com.netblocker.pro.MainActivity
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import kotlin.concurrent.thread

/**
 * Keeps every home-screen widget in sync with the firewall state and pushes
 * data-set refreshes to the blocked-apps list widget.
 */
object WidgetHelper {

    private const val TAG = "NetBlockerWidget"

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

    /**
     * Widget tap = pure background toggle, same behaviour as the Quick
     * Settings tile. The app is opened only when it is genuinely required:
     * the one-time VPN consent dialog, or a blocked background start.
     */
    fun toggleFromWidget(context: Context) {
        val appContext = context.applicationContext
        val turnOn = !Prefs.protectionOn(appContext)
        Prefs.setProtection(appContext, turnOn)
        updateAll(appContext)
        thread(name = "nb-widget-toggle") {
            if (turnOn) {
                val result = runCatching { Engine.startBlocking(appContext) }
                    .getOrElse { Engine.StartResult.FAILED }
                if (result == Engine.StartResult.VPN_CONSENT_NEEDED ||
                    result == Engine.StartResult.VPN_START_BLOCKED
                ) {
                    // One-time setup needs a visible activity — open the app.
                    try {
                        appContext.startActivity(
                            Intent(appContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(MainActivity.EXTRA_ENABLE_PROTECTION, true)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "could not open app for setup", e)
                    }
                }
            } else {
                runCatching { Engine.stop(appContext) }
            }
            updateAll(appContext)
            Engine.requestTileUpdate(appContext)
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
