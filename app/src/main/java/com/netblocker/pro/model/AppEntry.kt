package com.netblocker.pro.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val versionName: String,
    val icon: Drawable?,
    var policy: Int = POLICY_ALLOW
) {
    companion object {
        const val POLICY_ALLOW = 0
        const val POLICY_BLOCK_ALL = 1
        const val POLICY_BLOCK_WIFI = 2
        const val POLICY_BLOCK_DATA = 3
    }

    val blockAll: Boolean get() = policy == POLICY_BLOCK_ALL
    val affectsWifi: Boolean get() = policy == POLICY_BLOCK_ALL || policy == POLICY_BLOCK_WIFI
    val affectsData: Boolean get() = policy == POLICY_BLOCK_ALL || policy == POLICY_BLOCK_DATA
}
