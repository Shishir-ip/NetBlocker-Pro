package com.netblocker.pro.receiver

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.netblocker.pro.MainActivity
import com.netblocker.pro.R
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.firewall.Engine
import kotlin.concurrent.thread

/**
 * Quick Settings tile: flips protection on/off.
 *
 *  ON  → root devices get iptables rules; normal devices get the VPN engine.
 *        If the system VPN consent dialog is still required, the app is opened
 *        to show it (a tile cannot display that dialog itself).
 *  OFF → iptables rules are removed / the VPN disconnects — everything is
 *        back to normal.
 */
class ProtectionTileService : TileService() {

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        val ctx = applicationContext
        val turnOn = !Prefs.protectionOn(ctx)
        if (turnOn) {
            Prefs.setProtection(ctx, true)
            updateTile()
            thread(name = "nb-tile-on") {
                val result = runCatching { Engine.startBlocking(ctx) }
                    .getOrElse { Engine.StartResult.FAILED }
                if (result == Engine.StartResult.VPN_CONSENT_NEEDED) {
                    launchForConsent()
                }
            }
        } else {
            Prefs.setProtection(ctx, false)
            updateTile()
            thread(name = "nb-tile-off") {
                runCatching { Engine.stop(ctx) }
            }
        }
    }

    /** A tile cannot show the system VPN consent dialog — open the app to do it. */
    private fun launchForConsent() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_ENABLE_PROTECTION, true)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val ctx = applicationContext
        val active = Prefs.protectionOn(ctx)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (active) {
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = getString(if (Engine.usesRootEngine(ctx)) R.string.tile_sub_root else R.string.tile_sub_vpn)
            }
            tile.contentDescription = getString(R.string.tile_desc_on)
        } else {
            tile.contentDescription = getString(R.string.tile_desc_off)
        }
        tile.updateTile()
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, ProtectionTileService::class.java)
    }
}
