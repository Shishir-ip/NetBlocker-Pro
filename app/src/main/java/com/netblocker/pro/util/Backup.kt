package com.netblocker.pro.util

import android.content.Context
import android.net.Uri
import com.netblocker.pro.data.Prefs
import org.json.JSONObject

/**
 * Export / import rules as JSON documents via the Storage Access Framework.
 */
object Backup {

    fun exportJson(context: Context): String {
        val obj = JSONObject()
        obj.put("app", "NetBlocker Pro")
        obj.put("format", 1)
        obj.put("whitelist", Prefs.whitelistMode(context))
        val rules = JSONObject()
        for ((uid, policy) in Prefs.getRules(context)) {
            rules.put(uid.toString(), policy)
        }
        obj.put("rules", rules)
        return obj.toString(2)
    }

    /** Returns the number of imported rules, or -1 on failure. */
    fun importJson(context: Context, uri: Uri): Int {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return -1
            val obj = JSONObject(text)
            val rulesObj = obj.optJSONObject("rules") ?: return -1
            val rules = HashMap<Int, Int>()
            for (key in rulesObj.keys()) {
                rules[key.toInt()] = rulesObj.optInt(key, 0)
            }
            if (obj.has("whitelist")) {
                Prefs.setWhitelistMode(context, obj.optBoolean("whitelist", false))
            }
            Prefs.setRules(context, rules)
            rules.size
        } catch (e: Exception) {
            -1
        }
    }
}
