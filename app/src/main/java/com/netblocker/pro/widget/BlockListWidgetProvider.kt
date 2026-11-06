package com.netblocker.pro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import com.netblocker.pro.model.AppEntry
import com.netblocker.pro.ui.BlockListActivity
import kotlin.concurrent.thread

/**
 * Resizable home-screen widget listing the apps that are currently blocked
 * (blacklist mode) or whitelisted (whitelist mode). Bigger widget = more rows.
 *
 *  - header pill toggles protection
 *  - "+" opens the app picker
 *  - tapping a row removes that app from the list
 */
class BlockListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        WidgetHelper.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REMOVE) {
            val uid = intent.getIntExtra(EXTRA_UID, -1)
            if (uid > 0) {
                Prefs.setRule(context, uid, AppEntry.POLICY_ALLOW)
                WidgetHelper.notifyDataChanged(context)
                thread(name = "nb-widget-apply") { runCatching { Engine.applyRuleChanges(context) } }
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REMOVE = "com.netblocker.pro.widget.REMOVE_APP"
        const val EXTRA_UID = "com.netblocker.pro.widget.extra.UID"

        fun buildRemoteViews(context: Context, on: Boolean, count: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_list)
            rv.setTextViewText(
                R.id.widgetTitle,
                context.getString(
                    if (Prefs.whitelistMode(context)) R.string.widget_whitelist_title
                    else R.string.widget_blocked_title
                )
            )
            rv.setTextViewText(R.id.widgetCount, count.toString())
            rv.setInt(
                R.id.widgetTogglePill, "setBackgroundResource",
                if (on) R.drawable.widget_pill_on else R.drawable.widget_pill_off
            )
            rv.setTextViewText(
                R.id.widgetTogglePill,
                context.getString(if (on) R.string.widget_on else R.string.widget_off)
            )
            rv.setOnClickPendingIntent(R.id.widgetTogglePill, ToggleWidgetProvider.togglePendingIntent(context))
            rv.setOnClickPendingIntent(R.id.widgetAdd, BlockListActivity.pendingIntent(context))
            rv.setRemoteAdapter(R.id.widgetList, Intent(context, BlockListRemoteViewsService::class.java))
            rv.setEmptyView(R.id.widgetList, R.id.widgetEmpty)
            rv.setPendingIntentTemplate(R.id.widgetList, removePendingIntent(context))
            return rv
        }

        private fun removePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, BlockListWidgetProvider::class.java).setAction(ACTION_REMOVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
