package com.netblocker.pro.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import android.util.Log
import com.netblocker.pro.data.Prefs

/**
 * Debug-only self check: inflates both widget RemoteViews and runs the list
 * factory lifecycle in-process so any error is visible in logcat. No-op in
 * release builds.
 */
class DebugCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        when (intent.action) {
            ACTION_PIN_TOGGLE -> pin(context, ComponentName(context, ToggleWidgetProvider::class.java))
            ACTION_PIN_LIST -> pin(context, ComponentName(context, BlockListWidgetProvider::class.java))
        }
        val on = Prefs.protectionOn(context)
        val count = Prefs.getRules(context).size

        try {
            val toggle = ToggleWidgetProvider.buildRemoteViews(context, on)
            toggle.apply(context, FrameLayout(context))
            log("toggle RemoteViews apply OK")
        } catch (e: Throwable) {
            log("toggle RemoteViews apply FAILED: $e")
        }
        try {
            val list = BlockListWidgetProvider.buildRemoteViews(context, on, count)
            list.apply(context, FrameLayout(context))
            log("list RemoteViews apply OK")
        } catch (e: Throwable) {
            android.util.Log.e("NetBlockerDebug", "list RemoteViews apply FAILED", e)
            log("list RemoteViews apply FAILED: $e")
        }
        try {
            val factory = BlockListRemoteViewsService.Factory(context)
            factory.onCreate()
            factory.onDataSetChanged()
            log("factory rows=${factory.getCount()}")
            for (i in 0 until factory.getCount()) {
                factory.getViewAt(i)
            }
            factory.getLoadingView()
            log("factory lifecycle OK")
        } catch (e: Throwable) {
            log("factory lifecycle FAILED: $e")
        }
    }

    private fun pin(context: Context, component: ComponentName) {
        try {
            val mgr = android.appwidget.AppWidgetManager.getInstance(context)
            val ok = mgr.requestPinAppWidget(component, null, null)
            Log.i("NetBlockerDebug", "requestPinAppWidget($component) -> $ok")
        } catch (e: Throwable) {
            Log.e("NetBlockerDebug", "pin failed", e)
        }
    }

    private fun log(msg: String) {
        android.util.Log.i("NetBlockerDebug", msg)
    }

    companion object {
        const val ACTION_PIN_TOGGLE = "com.netblocker.pro.DEBUG_PIN_TOGGLE"
        const val ACTION_PIN_LIST = "com.netblocker.pro.DEBUG_PIN_LIST"
    }
}
