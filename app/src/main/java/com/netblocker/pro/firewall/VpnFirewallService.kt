package com.netblocker.pro.firewall

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.netblocker.pro.MainActivity
import com.netblocker.pro.R
import com.netblocker.pro.data.AppRepository
import com.netblocker.pro.data.Prefs
import com.netblocker.pro.model.AppEntry
import com.netblocker.pro.util.NotifHelper
import java.io.FileInputStream
import kotlin.concurrent.thread

/**
 * No-root blocking engine.
 *
 * Instead of forwarding traffic through the tunnel (which would require a
 * user-space TCP stack), this engine inverts the VPN's per-app filter:
 *
 *  - every app that is ALLOWED on the current network is added via
 *    [VpnService.Builder.addDisallowedApplication], so its traffic bypasses
 *    the tunnel and flows over the normal network untouched;
 *  - every app that is BLOCKED has no exclusion, so its packets enter the
 *    tunnel where they are read and discarded — the app simply has no network.
 *
 * Wi-Fi vs mobile data is handled by re-establishing the tunnel with a
 * different exclusion list whenever the default network type changes.
 */
class VpnFirewallService : VpnService() {

    companion object {
        private const val TAG = "VpnFirewall"
        private const val NET_NONE = 0
        private const val NET_WIFI = 1
        private const val NET_DATA = 2
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.netblocker.pro.action.STOP_VPN"
        const val ACTION_RECONFIGURE = "com.netblocker.pro.action.RECONFIGURE_VPN"

        @Volatile
        var isRunning = false
            private set
    }

    private val lock = Any()
    private var tun: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    private var lastAppliedNetwork = -1
    private var packageReceiver: BroadcastReceiver? = null

    private val connManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        registerNetworkCallbacks()
        registerPackageReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == ACTION_STOP || !Prefs.protectionOn(this)) {
            // Explicit stop — or a stale start that landed after the user turned
            // protection off. Make sure nothing survives: kill tunnel + service.
            Prefs.setProtection(this, false)
            NotifHelper.update(this)
            Engine.requestTileUpdate(this)
            synchronized(lock) {
                runCatching { tun?.close() }
                tun = null
            }
            stopSelf()
            return START_NOT_STICKY
        }
        requestEstablish(force = true)
        return START_STICKY
    }

    override fun onRevoke() {
        // The user disconnected the VPN or another VPN took over.
        Prefs.setProtection(this, false)
        NotifHelper.update(this)
        Engine.requestTileUpdate(this)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        isRunning = false
        synchronized(lock) {
            runCatching { tun?.close() }
            tun = null
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { connManager.unregisterNetworkCallback(netCallback) }
        packageReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }

    // ------------------------------------------------------------- establish

    private fun requestEstablish(force: Boolean = false) {
        thread(name = "nb-vpn-establish") { establish(force) }
    }

    private fun establish(force: Boolean = false) {
        synchronized(lock) {
            try {
                if (!isRunning || !Prefs.protectionOn(this)) {
                    stopSelf()
                    return
                }
                val type = currentNetworkType()
                if (!force && tun != null && type == lastAppliedNetwork) return

                val builder = Builder()
                    .setSession("NetBlocker Pro")
                    .setMtu(1500)
                    .addAddress("10.111.222.3", 32)
                    .addRoute("0.0.0.0", 0)
                runCatching { builder.addRoute("::", 0) }

                val disallowed = computeDisallowedPackages(type)
                for (pkg in disallowed) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (ignored: Exception) {
                    }
                }

                // The service may have been stopped while we were building the
                // exclusion list — never re-establish a dead service's tunnel.
                if (!isRunning) return

                val newTun = builder.establish()
                if (newTun == null) {
                    Log.e(TAG, "establish() returned null — VPN consent revoked?")
                    stopSelf()
                    return
                }
                if (!isRunning) {
                    // Stopped while establish() was in flight — kill the fresh
                    // tunnel immediately so the VPN truly disconnects.
                    runCatching { newTun.close() }
                    return
                }

                val old = tun
                tun = newTun
                lastAppliedNetwork = type
                runCatching { old?.close() }
                startReaderIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "establish failed", e)
            }
        }
    }

    /**
     * Packages whose traffic must bypass the tunnel (= allowed right now).
     * Everything not in this set is captured and discarded (= blocked).
     */
    private fun computeDisallowedPackages(networkType: Int): Set<String> {
        val whitelist = Prefs.whitelistMode(this)
        val allowSystem = Prefs.allowSystemWhitelist(this)
        val rules = Prefs.getRules(this)
        val disallowed = HashSet<String>()
        disallowed.add(packageName)
        disallowed.add("android")
        val apps = AppRepository.loadAppsBlocking(this, withIcons = false)
        for (app in apps) {
            val policy = rules[app.uid] ?: AppEntry.POLICY_ALLOW
            val blocked = if (whitelist) {
                when (policy) {
                    AppEntry.POLICY_BLOCK_ALL -> false
                    AppEntry.POLICY_BLOCK_WIFI -> networkType == NET_DATA
                    AppEntry.POLICY_BLOCK_DATA -> networkType == NET_WIFI
                    else -> true
                }
            } else {
                when (policy) {
                    AppEntry.POLICY_BLOCK_ALL -> true
                    AppEntry.POLICY_BLOCK_WIFI -> networkType == NET_WIFI
                    AppEntry.POLICY_BLOCK_DATA -> networkType == NET_DATA
                    else -> false
                }
            }
            if (!blocked || (whitelist && allowSystem && app.isSystem)) {
                disallowed.add(app.packageName)
            }
        }
        return disallowed
    }

    private fun currentNetworkType(): Int {
        var wifi = false
        var cell = false
        for (network in connManager.allNetworks) {
            val caps = connManager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                wifi = true
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                cell = true
            }
        }
        return when {
            wifi -> NET_WIFI
            cell -> NET_DATA
            else -> NET_NONE
        }
    }

    // ---------------------------------------------------------------- reader

    private fun startReaderIfNeeded() {
        if (readerThread?.isAlive == true) return
        readerThread = thread(name = "nb-vpn-reader", isDaemon = true) {
            val buffer = ByteArray(32768)
            while (isRunning) {
                val pfd = synchronized(lock) { tun } ?: break
                try {
                    FileInputStream(pfd.fileDescriptor).use { stream ->
                        while (isRunning) {
                            val current = synchronized(lock) { tun }
                            if (current !== pfd) break
                            val count = stream.read(buffer)
                            if (count < 0) break
                            // Discarded on purpose — blocked traffic never leaves the device.
                        }
                    }
                } catch (ignored: Exception) {
                    // fd was closed or swapped
                }
            }
        }
    }

    // ------------------------------------------------------ network + package

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            requestEstablish()
        }

        override fun onLost(network: Network) {
            requestEstablish()
        }
    }

    private fun registerNetworkCallbacks() {
        runCatching {
            connManager.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                netCallback
            )
            connManager.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build(),
                netCallback
            )
            connManager.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(),
                netCallback
            )
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_RESTARTED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                requestEstablish(force = true)
            }
        }
        packageReceiver = receiver
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    // ----------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        NotifHelper.ensureChannel(this)
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VpnFirewallService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotifHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_vpn_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
