package com.netblocker.pro

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.util.NotifHelper

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        NotifHelper.ensureChannel(this)
        applyTheme()
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (Prefs.theme(this)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
