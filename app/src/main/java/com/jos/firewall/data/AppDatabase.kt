package com.jos.firewall.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AppRuleEntity::class,
        FirewallRuleEntity::class,
        ConnectionLogEntity::class,
        DnsRuleEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun firewallDao(): FirewallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jos_firewall.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDefaultDnsRules(database.firewallDao())
                        populateDefaultFirewallRules(database.firewallDao())
                    }
                }
            }

            private suspend fun populateDefaultDnsRules(dao: FirewallDao) {
                val defaultAds = listOf(
                    DnsRuleEntity("ads.doubleclick.net", isBlocked = true, category = "ADVERTISING"),
                    DnsRuleEntity("google-analytics.com", isBlocked = true, category = "TRACKING"),
                    DnsRuleEntity("adservice.google.com", isBlocked = true, category = "ADVERTISING"),
                    DnsRuleEntity("pagead2.googlesyndication.com", isBlocked = true, category = "ADVERTISING"),
                    DnsRuleEntity("graph.facebook.com", isBlocked = true, category = "TRACKING"),
                    DnsRuleEntity("app-measurement.com", isBlocked = true, category = "TELEMETRY"),
                    DnsRuleEntity("telemetry.sdk.unity3d.com", isBlocked = true, category = "TELEMETRY"),
                    DnsRuleEntity("metrics.data.hicloud.com", isBlocked = true, category = "TELEMETRY"),
                    DnsRuleEntity("crashlytics.com", isBlocked = false, category = "DIAGNOSTICS"),
                    DnsRuleEntity("cloudflare-dns.com", isBlocked = false, category = "SYSTEM_DNS")
                )
                dao.insertDnsRules(defaultAds)
            }

            private suspend fun populateDefaultFirewallRules(dao: FirewallDao) {
                val defaultRules = listOf(
                    FirewallRuleEntity(
                        ruleName = "Block Insecure Telnet",
                        ruleType = RuleType.PORT,
                        pattern = "23",
                        protocol = "TCP",
                        direction = RuleDirection.OUTBOUND,
                        action = RuleAction.BLOCK,
                        isEnabled = true
                    ),
                    FirewallRuleEntity(
                        ruleName = "Block Unencrypted SMB",
                        ruleType = RuleType.PORT,
                        pattern = "445",
                        protocol = "TCP",
                        direction = RuleDirection.OUTBOUND,
                        action = RuleAction.BLOCK,
                        isEnabled = true
                    )
                )
                for (rule in defaultRules) {
                    dao.insertFirewallRule(rule)
                }
            }
        }
    }
}
