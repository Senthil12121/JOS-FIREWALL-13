package com.jos.firewall.firewall

import com.jos.firewall.data.AppDatabase
import com.jos.firewall.data.AppRuleEntity
import com.jos.firewall.data.FirewallRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * High-speed in-memory cache synchronized with Room for packet-level rule lookups.
 */
class RuleRepository(
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val dao = database.firewallDao()

    // Fast lookup cache for App Rules indexed by UID and PackageName
    private val appRuleByUid = ConcurrentHashMap<Int, AppRuleEntity>()
    private val appRuleByPkg = ConcurrentHashMap<String, AppRuleEntity>()

    // Fast lookup list for global Firewall Rules
    @Volatile
    private var cachedFirewallRules: List<FirewallRule> = emptyList()

    init {
        // Observe app rules
        scope.launch(Dispatchers.IO) {
            dao.getAllAppRulesFlow().collectLatest { rules ->
                appRuleByUid.clear()
                appRuleByPkg.clear()
                for (rule in rules) {
                    appRuleByUid[rule.uid] = rule
                    appRuleByPkg[rule.packageName] = rule
                }
            }
        }

        // Observe custom firewall rules
        scope.launch(Dispatchers.IO) {
            dao.getAllFirewallRulesFlow().collectLatest { entities ->
                cachedFirewallRules = entities.map { entity ->
                    FirewallRule(
                        id = entity.id,
                        name = entity.ruleName,
                        type = entity.ruleType,
                        pattern = entity.pattern,
                        direction = entity.direction,
                        action = entity.action,
                        protocol = entity.protocol,
                        isIpv6 = entity.isIpv6,
                        isEnabled = entity.isEnabled,
                        targetAppPackage = entity.targetAppPackage
                    )
                }
            }
        }
    }

    fun getAppRuleByUid(uid: Int): AppRuleEntity? = appRuleByUid[uid]

    fun getAppRuleByPackage(packageName: String): AppRuleEntity? = appRuleByPkg[packageName]

    fun getActiveRules(): List<FirewallRule> = cachedFirewallRules

    fun getAllAppRulesFlow(): Flow<List<AppRuleEntity>> = dao.getAllAppRulesFlow()

    fun getAllFirewallRulesFlow(): Flow<List<FirewallRuleEntity>> = dao.getAllFirewallRulesFlow()

    suspend fun updateAppRule(rule: AppRuleEntity) {
        dao.insertOrUpdateAppRule(rule)
    }

    suspend fun addFirewallRule(rule: FirewallRuleEntity) {
        dao.insertFirewallRule(rule)
    }

    suspend fun toggleFirewallRule(rule: FirewallRuleEntity) {
        dao.updateFirewallRule(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun deleteFirewallRule(rule: FirewallRuleEntity) {
        dao.deleteFirewallRule(rule)
    }

    suspend fun resetDefaults() {
        dao.deleteAllFirewallRules()
    }
}
