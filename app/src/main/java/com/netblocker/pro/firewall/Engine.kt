package com.netblocker.pro.firewall

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.receiver.ProtectionTileService
import com.netblocker.pro.util.NotifHelper
import com.netblocker.pro.widget.WidgetHelper

/**
 * Chooses and drives the active blocking engine.
 *
 *  - Root engine  : per-UID iptables rules (RuleApplier)
 *  - VPN engine   : local VPN whose tunnel captures only blocked apps; their
 *                   packets are discarded while allowed apps bypass the tunnel
 *                   entirely (VpnFirewallService)
 *
 * "Automatic" picks root when superuser is available and the VPN engine otherwise,
 * so the app works on normal, unrooted devices too.
 */
object Engine {

    const val METHOD_AUTO = 0
    const val METHOD_ROOT = 1
    const val METHOD_VPN = 2

    enum class StartResult { ROOT_STARTED, VPN_STARTED, VPN_CONSENT_NEEDED, VPN_START_BLOCKED, FAILED }

    data class State(val rootAvailable: Boolean, val usingRoot: Boolean, val active: Boolean)

    fun method(context: Context): Int = Prefs.blockingMethod(context)

    /** True when the relevant engine is the root iptables engine. */
    fun usesRootEngine(context: Context): Boolean = when (Prefs.blockingMethod(context)) {
        METHOD_ROOT -> true
        METHOD_VPN -> false
        else -> RootShell.isRootAvailable()
    }

    /** Refresh the Quick Settings tile and all home-screen widgets. */
    fun requestTileUpdate(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context.applicationContext,
                ProtectionTileService.componentName(context)
            )
        }
        WidgetHelper.updateAll(context.applicationContext)
    }

    /**
     * Enable protection with the configured engine.
     * Blocking call (root probe + iptables) — run on a background thread.
     */
    fun startBlocking(context: Context): StartResult {
        val appContext = context.applicationContext
        val result = startBlockingInternal(appContext)
        requestTileUpdate(appContext)
        return result
    }

    private fun startBlockingInternal(appContext: Context): StartResult {
        val useRoot = when (Prefs.blockingMethod(appContext)) {
            METHOD_VPN -> false
            else -> RootShell.isRootAvailable(force = true, timeoutSec = 15)
        }
        return if (useRoot) {
            if (RuleApplier.applyRules(appContext)) {
                NotifHelper.update(appContext)
                StartResult.ROOT_STARTED
            } else {
                StartResult.FAILED
            }
        } else {
            val consent = VpnService.prepare(appContext)
            if (consent != null) {
                StartResult.VPN_CONSENT_NEEDED
            } else if (startVpn(appContext)) {
                StartResult.VPN_STARTED
            } else {
                // Foreground-service start blocked from this (background) context.
                StartResult.VPN_START_BLOCKED
            }
        }
    }

    /**
     * Start the VPN engine (call only after consent, or when prepare() is null).
     * Returns false when the system blocks the start (e.g. background context) —
     * callers should then hand off to a visible activity.
     */
    fun startVpn(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, VpnFirewallService::class.java)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Disable protection: stops the VPN engine and removes any iptables rules. */
    fun stop(context: Context) {
        val appContext = context.applicationContext
        // CRITICAL: while a VPN session is active, the system holds a
        // BIND_AUTO_CREATE binding on our VpnService. stopService() alone
        // therefore does NOT destroy the service (onDestroy never runs) and
        // the tunnel stays up. Delivering an explicit STOP command reaches
        // the still-alive service, which closes the tunnel itself.
        runCatching {
            appContext.startService(
                Intent(appContext, VpnFirewallService::class.java)
                    .setAction(VpnFirewallService.ACTION_STOP)
            )
        }
        // Backstop for a service that is only "started" (not system-bound).
        appContext.stopService(Intent(appContext, VpnFirewallService::class.java))
        RuleApplier.clearRules(appContext)
        NotifHelper.update(appContext)
        requestTileUpdate(appContext)
    }

    /** Push per-app rule changes to the running engine. Call on a background thread. */
    fun applyRuleChanges(context: Context) {
        val appContext = context.applicationContext
        if (!Prefs.protectionOn(appContext)) return
        if (usesRootEngine(appContext)) {
            RuleApplier.applyRules(appContext)
        } else if (VpnFirewallService.isRunning) {
            appContext.startService(
                Intent(appContext, VpnFirewallService::class.java)
                    .setAction(VpnFirewallService.ACTION_RECONFIGURE)
            )
        }
    }

    /**
     * Make sure the firewall is enforcing the current rules right now.
     * Blocking call — run on a background thread.
     */
    fun applyNow(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!Prefs.protectionOn(appContext)) {
            stop(appContext)
            return true
        }
        return if (usesRootEngine(appContext)) {
            RuleApplier.applyRules(appContext)
        } else if (VpnFirewallService.isRunning) {
            appContext.startService(
                Intent(appContext, VpnFirewallService::class.java)
                    .setAction(VpnFirewallService.ACTION_RECONFIGURE)
            )
            true
        } else {
            val consent = VpnService.prepare(appContext)
            if (consent == null) {
                startVpn(appContext)
            } else {
                false
            }
        }
    }

    /**
     * Snapshot of engine state; auto-recovers the active engine when it is not
     * running but should be. Blocking call — run on a background thread.
     */
    fun status(context: Context): State {
        val appContext = context.applicationContext
        val root = RootShell.isRootAvailable()
        val usingRoot = when (Prefs.blockingMethod(appContext)) {
            METHOD_ROOT -> true
            METHOD_VPN -> false
            else -> root
        }
        val active = if (!Prefs.protectionOn(appContext)) {
            false
        } else if (usingRoot) {
            var st = RuleApplier.status(appContext)
            if (st == RuleApplier.Status.INACTIVE) {
                RuleApplier.applyRules(appContext)
                st = RuleApplier.status(appContext)
            }
            st == RuleApplier.Status.ACTIVE
        } else {
            if (!VpnFirewallService.isRunning) {
                val consent = VpnService.prepare(appContext)
                if (consent == null) startVpn(appContext)
            }
            VpnFirewallService.isRunning
        }
        // A start that races a "protection off" toggle self-destructs inside the
        // service (onStartCommand checks protection); reflect the real state here.
        val effectiveActive = active && Prefs.protectionOn(appContext)
        return State(root, usingRoot, effectiveActive)
    }
}
