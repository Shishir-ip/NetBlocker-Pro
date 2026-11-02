package com.netblocker.pro.firewall

import android.content.Context
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.model.AppEntry

/**
 * Applies per-UID network rules with iptables (no VPN). Creates dedicated
 * chains and dispatches by output interface:
 *
 *   OUTPUT -> NB_OUT -o wlan+|eth+  -> NB_WIFI  (Wi-Fi / ethernet)
 *                  -o rmnet+|ccmni+ -> NB_DATA  (mobile data)
 *
 * Blacklist mode: listed apps get REJECT on the networks they are blocked on.
 * Whitelist mode: everything is REJECTed unless the UID is explicitly allowed
 * (and, optionally, all system UIDs 0-9999).
 */
object RuleApplier {

    enum class Status { NO_ROOT, INACTIVE, ACTIVE }

    private const val CH_OUT = "NB_OUT"
    private const val CH_WIFI = "NB_WIFI"
    private const val CH_DATA = "NB_DATA"

    private val WIFI_IFS = listOf("wlan+", "eth+")
    private val DATA_IFS = listOf("rmnet+", "ccmni+")

    fun buildCommands(
        rules: Map<Int, Int>,
        whitelist: Boolean,
        allowSystem: Boolean,
        ipv6: Boolean
    ): List<String> {
        val cmds = ArrayList<String>()
        val bins = if (ipv6) listOf("iptables", "ip6tables") else listOf("iptables")
        for (bin in bins) {
            // Fresh chains
            cmds += "$bin -w -N $CH_OUT 2>/dev/null"
            cmds += "$bin -w -N $CH_WIFI 2>/dev/null"
            cmds += "$bin -w -N $CH_DATA 2>/dev/null"
            cmds += "$bin -w -F $CH_OUT 2>/dev/null"
            cmds += "$bin -w -F $CH_WIFI 2>/dev/null"
            cmds += "$bin -w -F $CH_DATA 2>/dev/null"
            // (Re)attach from OUTPUT
            cmds += "$bin -w -D OUTPUT -j $CH_OUT 2>/dev/null"
            cmds += "$bin -w -I OUTPUT 1 -j $CH_OUT"

            if (whitelist) {
                if (allowSystem) {
                    cmds += "$bin -w -A $CH_OUT -m owner --uid-owner 0-9999 -j RETURN"
                    cmds += "$bin -w -A $CH_WIFI -m owner --uid-owner 0-9999 -j RETURN"
                    cmds += "$bin -w -A $CH_DATA -m owner --uid-owner 0-9999 -j RETURN"
                }
                for ((uid, policy) in rules) {
                    if (policy == AppEntry.POLICY_BLOCK_ALL) {
                        cmds += "$bin -w -A $CH_OUT -m owner --uid-owner $uid -j RETURN"
                    }
                }
                for (itf in WIFI_IFS) cmds += "$bin -w -A $CH_OUT -o $itf -j $CH_WIFI"
                for (itf in DATA_IFS) cmds += "$bin -w -A $CH_OUT -o $itf -j $CH_DATA"
                cmds += "$bin -w -A $CH_OUT -j REJECT"

                for ((uid, policy) in rules) {
                    if (policy == AppEntry.POLICY_BLOCK_WIFI) {
                        cmds += "$bin -w -A $CH_WIFI -m owner --uid-owner $uid -j RETURN"
                    }
                }
                cmds += "$bin -w -A $CH_WIFI -j REJECT"

                for ((uid, policy) in rules) {
                    if (policy == AppEntry.POLICY_BLOCK_DATA) {
                        cmds += "$bin -w -A $CH_DATA -m owner --uid-owner $uid -j RETURN"
                    }
                }
                cmds += "$bin -w -A $CH_DATA -j REJECT"
            } else {
                for (itf in WIFI_IFS) cmds += "$bin -w -A $CH_OUT -o $itf -j $CH_WIFI"
                for (itf in DATA_IFS) cmds += "$bin -w -A $CH_OUT -o $itf -j $CH_DATA"
                for ((uid, policy) in rules) {
                    when (policy) {
                        AppEntry.POLICY_BLOCK_ALL -> cmds += "$bin -w -A $CH_OUT -m owner --uid-owner $uid -j REJECT"
                        AppEntry.POLICY_BLOCK_WIFI -> cmds += "$bin -w -A $CH_WIFI -m owner --uid-owner $uid -j REJECT"
                        AppEntry.POLICY_BLOCK_DATA -> cmds += "$bin -w -A $CH_DATA -m owner --uid-owner $uid -j REJECT"
                    }
                }
            }
        }
        return cmds
    }

    fun applyRules(context: Context): Boolean {
        if (!RootShell.isRootAvailable()) return false
        val cmds = buildCommands(
            rules = Prefs.getRules(context),
            whitelist = Prefs.whitelistMode(context),
            allowSystem = Prefs.allowSystemWhitelist(context),
            ipv6 = Prefs.blockIpv6(context)
        )
        val res = RootShell.run(cmds, timeoutSec = 90)
        return res.executed
    }

    fun clearRules(context: Context): Boolean {
        if (!RootShell.isRootAvailable()) return false
        val cmds = ArrayList<String>()
        for (bin in listOf("iptables", "ip6tables")) {
            cmds += "$bin -w -D OUTPUT -j $CH_OUT 2>/dev/null"
            for (ch in listOf(CH_OUT, CH_WIFI, CH_DATA)) {
                cmds += "$bin -w -F $ch 2>/dev/null"
                cmds += "$bin -w -X $ch 2>/dev/null"
            }
        }
        return RootShell.run(cmds).executed
    }

    /** Apply when protection is on, otherwise remove all rules. */
    fun applyOrClear(context: Context): Boolean =
        if (Prefs.protectionOn(context)) applyRules(context) else clearRules(context)

    fun status(context: Context): Status {
        if (!RootShell.isRootAvailable()) return Status.NO_ROOT
        if (!Prefs.protectionOn(context)) return Status.INACTIVE
        val res = RootShell.run(
            listOf(
                "iptables -w -S $CH_OUT 2>/dev/null",
                "iptables -w -S OUTPUT 2>/dev/null"
            )
        )
        val out = res.output
        val chainOk = out.lines().any { it.contains("-A $CH_OUT") || it.contains("-N $CH_OUT") }
        val jumpOk = out.lines().any { it.contains("-j $CH_OUT") }
        return if (chainOk && jumpOk) Status.ACTIVE else Status.INACTIVE
    }

    /** Raw rule dump for the diagnostics dialog. */
    fun dumpRules(): String {
        val res = RootShell.run(
            listOf(
                "iptables -w -S $CH_OUT 2>/dev/null",
                "iptables -w -S $CH_WIFI 2>/dev/null",
                "iptables -w -S $CH_DATA 2>/dev/null"
            )
        )
        return if (res.output.isBlank()) "No rules found." else res.output.trim()
    }
}
