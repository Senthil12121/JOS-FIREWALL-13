package com.jos.firewall.firewall

import com.jos.firewall.data.RuleAction
import java.net.InetAddress

data class FirewallDecision(
    val isAllowed: Boolean,
    val reason: String,
    val matchedRuleName: String? = null
)

/**
 * Core decision-making engine for network packet filtering.
 */
class FirewallEngine(
    private val ruleRepository: RuleRepository
) {
    var isFirewallActive: Boolean = true
    var isKillSwitchActive: Boolean = false
    var isScreenOffBlockEnabled: Boolean = false
    @Volatile
    var isScreenCurrentlyOn: Boolean = true

    /**
     * Determines whether to ALLOW or BLOCK a given flow.
     */
    fun evaluate(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        protocol: String,
        uid: Int,
        packageName: String?,
        isWifi: Boolean
    ): FirewallDecision {
        if (!isFirewallActive) {
            return FirewallDecision(true, "Firewall Disabled")
        }

        if (isScreenOffBlockEnabled && !isScreenCurrentlyOn) {
            return FirewallDecision(
                isAllowed = false,
                reason = "Blocked: Screen is off"
            )
        }

        // 1. Check Application-Level Rules (Per-App Firewall)
        val appRule = if (uid > 0) {
            ruleRepository.getAppRuleByUid(uid)
        } else if (packageName != null) {
            ruleRepository.getAppRuleByPackage(packageName)
        } else null

        if (appRule != null) {
            if (appRule.isBlocked) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "App '${appRule.appName}' blocked by master rule"
                )
            }
            if (isWifi && !appRule.isWifiAllowed) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "Wi-Fi traffic blocked for '${appRule.appName}'"
                )
            }
            if (!isWifi && !appRule.isMobileAllowed) {
                return FirewallDecision(
                    isAllowed = false,
                    reason = "Mobile data blocked for '${appRule.appName}'"
                )
            }
        }

        // 2. Check Custom IP, Port, and Protocol Rules
        val activeRules = ruleRepository.getActiveRules()
        for (rule in activeRules) {
            if (rule.matches(dstIp, dstPort, protocol, isWifi, packageName)) {
                val allowed = rule.action == RuleAction.ALLOW
                return FirewallDecision(
                    isAllowed = allowed,
                    reason = "Matched rule: ${rule.name} [${rule.action}]",
                    matchedRuleName = rule.name
                )
            }
        }

        // 3. Default Policy: ALLOW
        return FirewallDecision(
            isAllowed = true,
            reason = "Default Policy (Allow)"
        )
    }
}
