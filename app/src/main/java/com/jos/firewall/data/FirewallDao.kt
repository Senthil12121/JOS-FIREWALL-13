package com.jos.firewall.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FirewallDao {

    // App Rules
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllAppRulesFlow(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    suspend fun getAllAppRules(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppRule(packageName: String): AppRuleEntity?

    @Query("SELECT * FROM app_rules WHERE uid = :uid LIMIT 1")
    suspend fun getAppRuleByUid(uid: Int): AppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppRule(rule: AppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppRules(rules: List<AppRuleEntity>)

    // General Firewall Rules
    @Query("SELECT * FROM firewall_rules ORDER BY id DESC")
    fun getAllFirewallRulesFlow(): Flow<List<FirewallRuleEntity>>

    @Query("SELECT * FROM firewall_rules WHERE isEnabled = 1")
    suspend fun getActiveFirewallRules(): List<FirewallRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFirewallRule(rule: FirewallRuleEntity): Long

    @Update
    suspend fun updateFirewallRule(rule: FirewallRuleEntity)

    @Delete
    suspend fun deleteFirewallRule(rule: FirewallRuleEntity)

    @Query("DELETE FROM firewall_rules")
    suspend fun deleteAllFirewallRules()

    // Connection Logs
    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogsFlow(): Flow<List<ConnectionLogEntity>>

    @Query("SELECT * FROM connection_logs WHERE status = :status ORDER BY timestamp DESC LIMIT 200")
    fun getLogsByStatusFlow(status: String): Flow<List<ConnectionLogEntity>>

    @Insert
    suspend fun insertLog(log: ConnectionLogEntity)

    @Query("DELETE FROM connection_logs")
    suspend fun clearLogs()

    // DNS Rules
    @Query("SELECT * FROM dns_rules ORDER BY hitCount DESC, domain ASC")
    fun getAllDnsRulesFlow(): Flow<List<DnsRuleEntity>>

    @Query("SELECT * FROM dns_rules WHERE isBlocked = 1")
    suspend fun getBlockedDnsRules(): List<DnsRuleEntity>

    @Query("SELECT isBlocked FROM dns_rules WHERE domain = :domain LIMIT 1")
    suspend fun isDomainBlocked(domain: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDnsRules(rules: List<DnsRuleEntity>)

    @Query("UPDATE dns_rules SET hitCount = hitCount + 1 WHERE domain = :domain")
    suspend fun incrementDnsHit(domain: String)

    @Update
    suspend fun updateDnsRule(rule: DnsRuleEntity)
}
