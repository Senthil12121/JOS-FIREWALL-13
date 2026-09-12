package com.jos.firewall.vpn

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Serializable

enum class TunnelProtocol {
    DIRECT_LOCAL,
    WIREGUARD,
    SOCKS5_PROXY,
    HTTP_PROXY
}

/**
 * Data model for a Virtual Location or Outbound Relay Server in JOS Firewall.
 */
data class LocationNode(
    val id: String,
    val countryCode: String,
    val countryName: String,
    val cityName: String,
    val flagEmoji: String,
    val exitIp: String,
    val port: Int = 443,
    val protocol: TunnelProtocol = TunnelProtocol.DIRECT_LOCAL,
    val latencyMs: Int = 0,
    val isCustom: Boolean = false,
    val authUsername: String? = null,
    val authPassword: String? = null,
    val publicKey: String? = null,
    val endpoint: String? = null
) : Serializable {

    val isDirect: Boolean
        get() = protocol == TunnelProtocol.DIRECT_LOCAL

    companion object {
        val DIRECT = LocationNode(
            id = "direct",
            countryCode = "LOC",
            countryName = "Local Device",
            cityName = "Direct Carrier Egress",
            flagEmoji = "⚡",
            exitIp = "Direct Real IP",
            protocol = TunnelProtocol.DIRECT_LOCAL,
            latencyMs = 0
        )

        val DEFAULT_LOCATIONS = listOf(
            DIRECT,
            LocationNode(
                id = "us-nyc",
                countryCode = "US",
                countryName = "United States",
                cityName = "New York",
                flagEmoji = "🇺🇸",
                exitIp = "198.51.100.42",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 28
            ),
            LocationNode(
                id = "us-lax",
                countryCode = "US",
                countryName = "United States",
                cityName = "Los Angeles",
                flagEmoji = "🇺🇸",
                exitIp = "142.250.80.45",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 45
            ),
            LocationNode(
                id = "gb-lon",
                countryCode = "GB",
                countryName = "United Kingdom",
                cityName = "London",
                flagEmoji = "🇬🇧",
                exitIp = "185.199.108.153",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 32
            ),
            LocationNode(
                id = "de-fra",
                countryCode = "DE",
                countryName = "Germany",
                cityName = "Frankfurt",
                flagEmoji = "🇩🇪",
                exitIp = "140.82.121.4",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 22
            ),
            LocationNode(
                id = "jp-tyo",
                countryCode = "JP",
                countryName = "Japan",
                cityName = "Tokyo",
                flagEmoji = "🇯🇵",
                exitIp = "133.242.18.90",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 64
            ),
            LocationNode(
                id = "sg-sin",
                countryCode = "SG",
                countryName = "Singapore",
                cityName = "Singapore",
                flagEmoji = "🇸🇬",
                exitIp = "103.253.144.10",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 58
            ),
            LocationNode(
                id = "ch-zrh",
                countryCode = "CH",
                countryName = "Switzerland",
                cityName = "Zurich",
                flagEmoji = "🇨🇭",
                exitIp = "194.230.150.12",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 25
            ),
            LocationNode(
                id = "nl-ams",
                countryCode = "NL",
                countryName = "Netherlands",
                cityName = "Amsterdam",
                flagEmoji = "🇳🇱",
                exitIp = "188.166.42.89",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 20
            ),
            LocationNode(
                id = "in-bom",
                countryCode = "IN",
                countryName = "India",
                cityName = "Mumbai",
                flagEmoji = "🇮🇳",
                exitIp = "103.21.244.15",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 48
            ),
            LocationNode(
                id = "ca-tor",
                countryCode = "CA",
                countryName = "Canada",
                cityName = "Toronto",
                flagEmoji = "🇨🇦",
                exitIp = "192.99.148.22",
                port = 51820,
                protocol = TunnelProtocol.WIREGUARD,
                latencyMs = 38
            )
        )
    }
}

/**
 * Manages the currently selected virtual location or custom outbound proxy.
 */
class LocationManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jos_location_prefs", Context.MODE_PRIVATE)

    private val _currentLocationFlow = MutableStateFlow(loadCurrentLocation())
    val currentLocationFlow: StateFlow<LocationNode> = _currentLocationFlow.asStateFlow()

    fun setLocation(node: LocationNode) {
        prefs.edit().apply {
            putString("selected_id", node.id)
            putString("selected_country", node.countryName)
            putString("selected_city", node.cityName)
            putString("selected_ip", node.exitIp)
            putInt("selected_port", node.port)
            putString("selected_protocol", node.protocol.name)
            putInt("selected_latency", node.latencyMs)
            apply()
        }
        _currentLocationFlow.value = node
    }

    private fun loadCurrentLocation(): LocationNode {
        val id = prefs.getString("selected_id", LocationNode.DIRECT.id) ?: LocationNode.DIRECT.id
        return LocationNode.DEFAULT_LOCATIONS.find { it.id == id } ?: LocationNode.DIRECT
    }

    companion object {
        @Volatile
        private var instance: LocationManager? = null

        fun getInstance(context: Context): LocationManager {
            return instance ?: synchronized(this) {
                instance ?: LocationManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
