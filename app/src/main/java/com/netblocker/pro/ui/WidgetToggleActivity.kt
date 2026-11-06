package com.netblocker.pro.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import com.netblocker.pro.firewall.RootShell
import com.netblocker.pro.widget.WidgetHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * Invisible trampoline launched by the home-screen widgets. Toggles protection
 * and finishes immediately; only stays visible when the system VPN consent
 * dialog has to be shown.
 */
class WidgetToggleActivity : AppCompatActivity() {

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && Prefs.protectionOn(this)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    Engine.startVpn(this@WidgetToggleActivity)
                    WidgetHelper.updateAll(this@WidgetToggleActivity)
                    withContext(Dispatchers.Main) { finish() }
                }
            } else {
                if (result.resultCode != Activity.RESULT_OK) {
                    Prefs.setProtection(this, false)
                    WidgetHelper.updateAll(this)
                }
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val turnOn = !Prefs.protectionOn(this)
        Prefs.setProtection(this, turnOn)
        WidgetHelper.updateAll(this)

        if (!turnOn) {
            lifecycleScope.launch(Dispatchers.IO) {
                Engine.stop(this@WidgetToggleActivity)
                withContext(Dispatchers.Main) { finish() }
            }
            return
        }

        RootShell.invalidateRoot()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { Engine.startBlocking(this@WidgetToggleActivity) }
                .getOrElse { Engine.StartResult.FAILED }
            withContext(Dispatchers.Main) {
                when (result) {
                    Engine.StartResult.VPN_CONSENT_NEEDED, Engine.StartResult.VPN_START_BLOCKED -> {
                        val consent = VpnService.prepare(this@WidgetToggleActivity)
                        if (consent != null) {
                            consentLauncher.launch(consent)
                        } else if (!Engine.startVpn(this@WidgetToggleActivity)) {
                            finish()
                        }
                    }
                    else -> finish()
                }
            }
        }
    }

    companion object {
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 11,
            Intent(context, WidgetToggleActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
