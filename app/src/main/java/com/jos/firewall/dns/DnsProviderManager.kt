package com.jos.firewall.dns

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DnsProvider(
    val id: String,
    val displayName: String,
    val primaryIp: String,
    val secondaryIp: String
) {
    companion object {
        val CLOUDFLARE = DnsProvider("cloudflare", "Cloudflare (1.1.1.1)", "1.1.1.1", "1.0.0.1")
        val GOOGLE = DnsProvider("google", "Google (8.8.8.8)", "8.8.8.8", "8.8.4.4")
        val QUAD9 = DnsProvider("quad9", "Quad9 (9.9.9.9)", "9.9.9.9", "149.112.112.112")
        val OPENDNS = DnsProvider("opendns", "OpenDNS (208.67.222.222)", "208.67.222.222", "208.67.220.220")
        val ADGUARD = DnsProvider("adguard", "AdGuard DNS (94.140.14.14)", "94.140.14.14", "94.140.15.15")

        val PRESETS = listOf(CLOUDFLARE, ADGUARD, GOOGLE, QUAD9, OPENDNS)

        fun custom(ip: String): DnsProvider = DnsProvider("custom", "Custom ($ip)", ip, ip)
    }
}

/**
 * Persists the user's chosen upstream DNS provider and exposes live updates
 * so the running DnsEngine can react without needing a VPN restart.
 */
class DnsProviderManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jos_dns_provider_prefs", Context.MODE_PRIVATE)

    private val _currentProviderFlow = MutableStateFlow(loadProvider())
    val currentProviderFlow: StateFlow<DnsProvider> = _currentProviderFlow.asStateFlow()

    fun setProvider(provider: DnsProvider) {
        prefs.edit().apply {
            putString("provider_id", provider.id)
            putString("provider_name", provider.displayName)
            putString("provider_primary_ip", provider.primaryIp)
            putString("provider_secondary_ip", provider.secondaryIp)
            apply()
        }
        _currentProviderFlow.value = provider
    }

    fun setCustomIp(ip: String) = setProvider(DnsProvider.custom(ip))

    private fun loadProvider(): DnsProvider {
        val id = prefs.getString("provider_id", DnsProvider.CLOUDFLARE.id)
        if (id == "custom") {
            val ip = prefs.getString("provider_primary_ip", "1.1.1.1") ?: "1.1.1.1"
            return DnsProvider.custom(ip)
        }
        return DnsProvider.PRESETS.find { it.id == id } ?: DnsProvider.CLOUDFLARE
    }

    companion object {
        @Volatile
        private var instance: DnsProviderManager? = null

        fun getInstance(context: Context): DnsProviderManager {
            return instance ?: synchronized(this) {
                instance ?: DnsProviderManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
