package com.netblocker.pro.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.netblocker.pro.R
import com.netblocker.pro.data.AppRepository
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.model.AppEntry

/**
 * Backing list for the blocked-apps widget: every app whose rule is set
 * (blocked on any network in blacklist mode, whitelisted in whitelist mode).
 */
class BlockListRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    class Factory(private val ctx: Context) : RemoteViewsFactory {

        private data class Row(val uid: Int, val label: String, val icon: Bitmap?)

        private var rows: List<Row> = emptyList()

        override fun onCreate() {
            onDataSetChanged()
        }

        override fun onDataSetChanged() {
            val rules = Prefs.getRules(ctx)
            val pm = ctx.packageManager
            rows = AppRepository.loadAppsBlocking(ctx, withIcons = false)
                .filter { (rules[it.uid] ?: AppEntry.POLICY_ALLOW) != AppEntry.POLICY_ALLOW }
                .sortedBy { it.label.lowercase() }
                .map { app ->
                    val icon = try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (e: Exception) {
                        null
                    }
                    Row(app.uid, app.label, icon?.toBitmap())
                }
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_list_item)
            val row = rows.getOrNull(position) ?: return rv
            rv.setTextViewText(R.id.itemLabel, row.label)
            row.icon?.let { rv.setImageViewBitmap(R.id.itemIcon, it) }
            rv.setOnClickFillInIntent(
                R.id.itemRoot,
                Intent().putExtra(BlockListWidgetProvider.EXTRA_UID, row.uid)
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.uid?.toLong() ?: position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
