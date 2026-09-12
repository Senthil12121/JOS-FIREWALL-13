package com.jos.firewall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.jos.firewall.vpn.FirewallVpnService

/**
 * Automatically activates JOS Firewall when device completes boot, if configured in settings.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("jos_firewall_prefs", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", true)

            if (startOnBoot) {
                // Ensure VPN permission is already granted before launching service
                val vpnIntent = VpnService.prepare(context)
                if (vpnIntent == null) {
                    FirewallVpnService.startService(context)
                }
            }
        }
    }
}
