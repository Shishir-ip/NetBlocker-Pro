package com.netblocker.pro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.RuleApplier
import kotlin.concurrent.thread

/**
 * Reapplies iptables rules after boot when enabled and protection is on.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.applyOnBoot(context) || !Prefs.protectionOn(context)) return

        val result = goAsync()
        thread(name = "nb-boot-apply") {
            try {
                RuleApplier.applyRules(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
