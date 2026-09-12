package com.jos.firewall.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.jos.firewall.MainActivity
import com.jos.firewall.R
import com.jos.firewall.data.AppDatabase
import com.jos.firewall.dns.DnsEngine
import com.jos.firewall.firewall.FirewallEngine
import com.jos.firewall.firewall.RuleRepository
import com.jos.firewall.logging.PacketLogger
import com.jos.firewall.stats.TrafficStatsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main Android VpnService implementation enforcing local TUN packet filtering.
 */
class FirewallVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnEngine: VpnEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var ruleRepository: RuleRepository
    private lateinit var firewallEngine: FirewallEngine
    private lateinit var dnsEngine: DnsEngine
    private lateinit var packetLogger: PacketLogger

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var screenStateReceiver: android.content.BroadcastReceiver? = null

    companion object {
        const val ACTION_START = "com.jos.firewall.START"
        const val ACTION_STOP = "com.jos.firewall.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jos_firewall_channel"

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        val statsManager = TrafficStatsManager()

        fun startService(context: Context) {
            val intent = Intent(context, FirewallVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FirewallVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext, serviceScope)
        ruleRepository = RuleRepository(database, serviceScope)
        firewallEngine = FirewallEngine(ruleRepository)
        dnsEngine = DnsEngine(database, serviceScope, applicationContext)
        packetLogger = PacketLogger(database, serviceScope)

        registerNetworkListener()
        registerScreenStateListener()
    }

    private fun registerScreenStateListener() {
        val prefs = getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
        firewallEngine.isScreenOffBlockEnabled = prefs.getBoolean("block_on_screen_off", false)

        screenStateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> firewallEngine.isScreenCurrentlyOn = false
                    Intent.ACTION_SCREEN_ON -> firewallEngine.isScreenCurrentlyOn = true
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        // Re-read the setting each time it changes, without needing a VPN restart
        serviceScope.launch {
            while (isActive) {
                firewallEngine.isScreenOffBlockEnabled = prefs.getBoolean("block_on_screen_off", false)
                delay(2000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundVpn()
        return START_STICKY
    }

    private fun startForegroundVpn() {
        createNotificationChannel()
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startVpnEngine()
    }

    private fun startVpnEngine() {
        if (vpnInterface != null) return

        try {
            val dnsProvider = com.jos.firewall.dns.DnsProviderManager.getInstance(applicationContext).currentProviderFlow.value

            val builder = Builder()
                .setSession("JOS Firewall")
                .setMtu(1500)
                // IPv4 Configuration
                .addAddress("10.1.10.1", 24)
                .addRoute("0.0.0.0", 0)
                // IPv6 Configuration
                .addAddress("fd00:1::1", 64)
                .addRoute("::", 0)
                // DNS Routing (follows the user's selected provider)
                .addDnsServer(dnsProvider.primaryIp)
                .addDnsServer(dnsProvider.secondaryIp)
                .setBlocking(true)

            // Configure app to allow its own traffic through without looping
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            vpnInterface = builder.establish()

            vpnInterface?.let { pfd ->
                vpnEngine = VpnEngine(
                    vpnService = this,
                    vpnInterface = pfd,
                    firewallEngine = firewallEngine,
                    dnsEngine = dnsEngine,
                    packetLogger = packetLogger,
                    statsManager = statsManager
                )
                vpnEngine?.start()
                _isRunningFlow.value = true
            }
        } catch (e: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        _isRunningFlow.value = false
        vpnEngine?.stop()
        vpnEngine = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        unregisterNetworkListener()
        screenStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerNetworkListener() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Keep underlying active network bound for protected sockets
                try {
                    setUnderlyingNetworks(arrayOf(network))
                } catch (_: Exception) {}
            }

            override fun onLost(network: Network) {
                try {
                    setUnderlyingNetworks(null)
                } catch (_: Exception) {}
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
    }

    private fun unregisterNetworkListener() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_shield_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
