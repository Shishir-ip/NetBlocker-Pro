package com.netblocker.pro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import com.netblocker.pro.firewall.RuleApplier
import kotlin.concurrent.thread

/**
 * Reapplies the firewall after boot when enabled and protection is on.
 * Root devices get iptables rules; everyone else gets the VPN engine
 * (which can start silently once the user has granted VPN consent).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.applyOnBoot(context) || !Prefs.protectionOn(context)) return

        val appContext = context.applicationContext
        val result = goAsync()
        thread(name = "nb-boot-apply") {
            try {
                if (Engine.usesRootEngine(appContext)) {
                    RuleApplier.applyRules(appContext)
                } else if (VpnService.prepare(appContext) == null) {
                    Engine.startVpn(appContext)
                }
            } catch (ignored: Exception) {
            } finally {
                result.finish()
            }
        }
    }
}
