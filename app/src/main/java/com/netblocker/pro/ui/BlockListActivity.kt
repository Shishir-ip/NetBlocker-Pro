package com.netblocker.pro.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.netblocker.pro.R
import com.netblocker.pro.data.AppRepository
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import com.netblocker.pro.model.AppEntry
import com.netblocker.pro.widget.WidgetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * Full-screen picker opened from the blocked-apps widget's "+" button:
 * every installed app with a switch to block/unblock it.
 */
class BlockListActivity : AppCompatActivity() {

    private var allApps: List<AppEntry> = emptyList()
    private var adapter: AppAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_list)

        val root = findViewById<LinearLayout>(R.id.blockListRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val recycler = findViewById<RecyclerView>(R.id.blockRecycler)
        adapter = AppAdapter(
            onToggle = { app, blocked -> toggle(app, blocked) },
            onDetail = {},
            onDismissHint = {}
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<TextView>(R.id.blockListTitle).text = getString(
            if (Prefs.whitelistMode(this)) R.string.widget_whitelist_title else R.string.widget_blocked_title
        )
        findViewById<View>(R.id.blockListSubtitle).visibility = View.VISIBLE

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            allApps = AppRepository.loadApps(this@BlockListActivity)
            submit()
        }
    }

    private fun submit() {
        adapter?.submit(allApps, hint = false, whitelistMode = Prefs.whitelistMode(this))
    }

    private fun toggle(app: AppEntry, blocked: Boolean) {
        val newPolicy = if (blocked) AppEntry.POLICY_BLOCK_ALL else AppEntry.POLICY_ALLOW
        Prefs.setRule(this, app.uid, newPolicy)
        allApps = allApps.map { if (it.uid == app.uid) it.copy(policy = newPolicy) else it }
        submit()
        thread(name = "nb-picker-apply") { runCatching { Engine.applyRuleChanges(this) } }
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { WidgetHelper.notifyDataChanged(this@BlockListActivity) }
        }
    }

    companion object {
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 12,
            Intent(context, BlockListActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
