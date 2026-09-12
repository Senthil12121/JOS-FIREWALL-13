package com.jos.firewall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-application firewall rule entity
 */
@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val appName: String,
    val isWifiAllowed: Boolean = true,
    val isMobileAllowed: Boolean = true,
    val isBlocked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Granular IP / Port / Domain / Protocol firewall rule
 */
enum class RuleType {
    IP,
    PORT,
    DOMAIN,
    PROTOCOL
}

enum class RuleDirection {
    INBOUND,
    OUTBOUND,
    BOTH
}

enum class RuleAction {
    ALLOW,
    BLOCK
}

@Entity(tableName = "firewall_rules")
data class FirewallRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleName: String,
    val ruleType: RuleType,
    val pattern: String,
    val direction: RuleDirection = RuleDirection.OUTBOUND,
    val action: RuleAction = RuleAction.BLOCK,
    val protocol: String = "ANY", // TCP, UDP, ICMP, ANY
    val isIpv6: Boolean = false,
    val isEnabled: Boolean = true,
    val targetAppPackage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Historical connection log for real-time and retrospective auditing
 */
@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String?,
    val appName: String?,
    val uid: Int,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val protocol: String,
    val status: String, // ALLOWED or BLOCKED
    val reason: String,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DNS sinkhole rule entity
 */
@Entity(tableName = "dns_rules")
data class DnsRuleEntity(
    @PrimaryKey val domain: String,
    val isBlocked: Boolean = true,
    val category: String = "ADVERTISING",
    val hitCount: Long = 0,
    val addedAt: Long = System.currentTimeMillis()
)
