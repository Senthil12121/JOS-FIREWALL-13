package com.jos.firewall.firewall

import com.jos.firewall.data.RuleAction
import com.jos.firewall.data.RuleDirection
import com.jos.firewall.data.RuleType
import java.net.InetAddress

/**
 * Runtime representation of evaluated firewall rules.
 */
data class FirewallRule(
    val id: Long,
    val name: String,
    val type: RuleType,
    val pattern: String,
    val direction: RuleDirection = RuleDirection.OUTBOUND,
    val action: RuleAction = RuleAction.BLOCK,
    val protocol: String = "ANY",
    val isIpv6: Boolean = false,
    val isEnabled: Boolean = true,
    val targetAppPackage: String? = null
) {
    fun matches(
        ip: InetAddress,
        port: Int,
        protoName: String,
        isWifi: Boolean,
        appPackage: String?
    ): Boolean {
        if (!isEnabled) return false

        // Check app scope
        if (targetAppPackage != null && targetAppPackage != appPackage) {
            return false
        }

        // Check protocol match
        if (protocol != "ANY" && !protocol.equals(protoName, ignoreCase = true)) {
            return false
        }

        return when (type) {
            RuleType.PORT -> {
                try {
                    pattern.toInt() == port
                } catch (_: Exception) {
                    false
                }
            }
            RuleType.IP -> {
                ip.hostAddress == pattern || pattern == "*"
            }
            RuleType.PROTOCOL -> {
                protocol.equals(protoName, ignoreCase = true)
            }
            RuleType.DOMAIN -> {
                // Evaluated during DNS inspection
                false
            }
        }
    }
}
