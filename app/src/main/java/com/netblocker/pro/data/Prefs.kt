package com.netblocker.pro.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Central settings + per-UID rule storage (SharedPreferences backed).
 */
object Prefs {

    private const val FILE = "netblocker_prefs"
    private const val KEY_PROTECTION = "protection"
    private const val KEY_WHITELIST = "whitelist_mode"
    private const val KEY_ALLOW_SYSTEM = "whitelist_allow_system"
    private const val KEY_IPV6 = "block_ipv6"
    private const val KEY_BOOT = "apply_on_boot"
    private const val KEY_NOTIFICATION = "persistent_notification"
    private const val KEY_THEME = "theme"
    private const val KEY_HINT = "hint_dismissed"
    private const val KEY_RULES = "rules"

    private var cached: SharedPreferences? = null

    private fun sp(context: Context): SharedPreferences =
        cached ?: context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).also { cached = it }

    // ----- Firewall state -----

    fun protectionOn(context: Context): Boolean = sp(context).getBoolean(KEY_PROTECTION, false)
    fun setProtection(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_PROTECTION, value).apply()

    fun whitelistMode(context: Context): Boolean = sp(context).getBoolean(KEY_WHITELIST, false)
    fun setWhitelistMode(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_WHITELIST, value).apply()

    fun allowSystemWhitelist(context: Context): Boolean = sp(context).getBoolean(KEY_ALLOW_SYSTEM, true)
    fun setAllowSystemWhitelist(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_ALLOW_SYSTEM, value).apply()

    fun blockIpv6(context: Context): Boolean = sp(context).getBoolean(KEY_IPV6, true)
    fun setBlockIpv6(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_IPV6, value).apply()

    fun applyOnBoot(context: Context): Boolean = sp(context).getBoolean(KEY_BOOT, true)
    fun setApplyOnBoot(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_BOOT, value).apply()

    fun showNotification(context: Context): Boolean = sp(context).getBoolean(KEY_NOTIFICATION, true)
    fun setShowNotification(context: Context, value: Boolean) = sp(context).edit().putBoolean(KEY_NOTIFICATION, value).apply()

    // ----- Appearance -----

    /** 0 = follow system, 1 = light, 2 = dark */
    fun theme(context: Context): Int = sp(context).getInt(KEY_THEME, 0)
    fun setTheme(context: Context, value: Int) = sp(context).edit().putInt(KEY_THEME, value).apply()

    // ----- Hint -----

    fun hintDismissed(context: Context): Boolean = sp(context).getBoolean(KEY_HINT, false)
    fun setHintDismissed(context: Context) = sp(context).edit().putBoolean(KEY_HINT, true).apply()

    // ----- Rules: uid -> policy -----

    fun getRules(context: Context): Map<Int, Int> {
        val json = sp(context).getString(KEY_RULES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val out = HashMap<Int, Int>(obj.length())
            for (key in obj.keys()) {
                out[key.toInt()] = obj.optInt(key, 0)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setRule(context: Context, uid: Int, policy: Int) {
        val rules = HashMap(getRules(context))
        if (policy == 0) rules.remove(uid) else rules[uid] = policy
        saveRules(context, rules)
    }

    fun setRules(context: Context, rules: Map<Int, Int>) {
        saveRules(context, HashMap(rules))
    }

    fun clearRules(context: Context) {
        saveRules(context, HashMap())
    }

    private fun saveRules(context: Context, rules: Map<Int, Int>) {
        val obj = JSONObject()
        for ((uid, policy) in rules) obj.put(uid.toString(), policy)
        sp(context).edit().putString(KEY_RULES, obj.toString()).apply()
    }
}
