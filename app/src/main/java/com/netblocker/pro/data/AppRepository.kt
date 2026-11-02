package com.netblocker.pro.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.netblocker.pro.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRepository {

    suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        loadAppsBlocking(context)
    }

    fun loadAppsBlocking(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val rules = Prefs.getRules(context)
        val result = ArrayList<AppEntry>()
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }
        for (ri in packages) {
            if (ri.packageName == context.packageName) continue
            val ai = ri.applicationInfo ?: continue
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (e: Exception) {
                ri.packageName
            }
            val icon = try {
                ai.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            result += AppEntry(
                packageName = ri.packageName,
                label = label,
                uid = ai.uid,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                versionName = ri.versionName ?: "",
                icon = icon,
                policy = rules[ai.uid] ?: AppEntry.POLICY_ALLOW
            )
        }
        result.sortBy { it.label.lowercase() }
        return result
    }
}
