package com.netblocker.pro

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.netblocker.pro.ui.AppsFragment
import com.netblocker.pro.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var appsFragment: AppsFragment
    private lateinit var settingsFragment: SettingsFragment
    private var selectedTab = 0
    private var pendingEnableRequest = false
    private var pendingDisableRequest = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingEnableRequest = intent?.getBooleanExtra(EXTRA_ENABLE_PROTECTION, false) ?: false
        pendingDisableRequest = intent?.getBooleanExtra(EXTRA_DISABLE_PROTECTION, false) ?: false

        val root = findViewById<LinearLayout>(R.id.root)
        val tabbar = findViewById<LinearLayout>(R.id.tabbar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(0, bars.top, 0, 0)
            tabbar.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        appsFragment = fm.findFragmentByTag(TAG_APPS) as? AppsFragment
            ?: AppsFragment().also { tx.add(R.id.container, it, TAG_APPS) }
        settingsFragment = fm.findFragmentByTag(TAG_SETTINGS) as? SettingsFragment
            ?: SettingsFragment().also { tx.add(R.id.container, it, TAG_SETTINGS) }

        selectedTab = savedInstanceState?.getInt(KEY_TAB, 0) ?: 0
        if (selectedTab == 0) tx.show(appsFragment).hide(settingsFragment)
        else tx.show(settingsFragment).hide(appsFragment)
        tx.commit()

        findViewById<LinearLayout>(R.id.tabApps).setOnClickListener { selectTab(0) }
        findViewById<LinearLayout>(R.id.tabSettings).setOnClickListener { selectTab(1) }
        tintTabs()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, selectedTab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ENABLE_PROTECTION, false)) {
            pendingEnableRequest = true
            window.decorView.post { appsFragment.maybeConsumeEnableRequest() }
        }
        if (intent.getBooleanExtra(EXTRA_DISABLE_PROTECTION, false)) {
            pendingDisableRequest = true
            window.decorView.post { appsFragment.maybeConsumeDisableRequest() }
        }
    }

    /** Consumed by AppsFragment to run the enable-protection flow (tile handoff). */
    fun consumeEnableRequest(): Boolean {
        val value = pendingEnableRequest
        pendingEnableRequest = false
        return value
    }

    /** Consumed by AppsFragment to run the disable-protection flow. */
    fun consumeDisableRequest(): Boolean {
        val value = pendingDisableRequest
        pendingDisableRequest = false
        return value
    }

    fun selectTab(index: Int) {
        if (index == selectedTab) return
        selectedTab = index
        val tx = supportFragmentManager.beginTransaction()
        if (index == 0) tx.show(appsFragment).hide(settingsFragment)
        else tx.show(settingsFragment).hide(appsFragment)
        tx.commit()
        tintTabs()
    }

    private fun tintTabs() {
        val sel = ContextCompat.getColor(this, R.color.ios_blue)
        val unsel = ContextCompat.getColor(this, R.color.ios_secondary)
        val a = selectedTab == 0
        findViewById<ImageView>(R.id.tabIconApps).setColorFilter(if (a) sel else unsel)
        findViewById<TextView>(R.id.tabTextApps).setTextColor(if (a) sel else unsel)
        findViewById<ImageView>(R.id.tabIconSettings).setColorFilter(if (a) unsel else sel)
        findViewById<TextView>(R.id.tabTextSettings).setTextColor(if (a) unsel else sel)
    }

    companion object {
        const val EXTRA_ENABLE_PROTECTION = "com.netblocker.pro.extra.ENABLE_PROTECTION"
        const val EXTRA_DISABLE_PROTECTION = "com.netblocker.pro.extra.DISABLE_PROTECTION"
        private const val TAG_APPS = "apps"
        private const val TAG_SETTINGS = "settings"
        private const val KEY_TAB = "selected_tab"
    }
}
