package com.jos.firewall.logging

import com.jos.firewall.data.AppDatabase
import com.jos.firewall.data.ConnectionLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * High-throughput asynchronous logger for audited network flows.
 */
class PacketLogger(
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val logChannel = Channel<ConnectionLogEntity>(capacity = 1000)
    private val dao = database.firewallDao()

    init {
        // Consumer coroutine writes logs in batches
        scope.launch(Dispatchers.IO) {
            for (log in logChannel) {
                try {
                    dao.insertLog(log)
                } catch (_: Exception) {}
            }
        }
    }

    fun logConnection(
        packageName: String?,
        appName: String?,
        uid: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        protocol: String,
        isAllowed: Boolean,
        reason: String,
        rxBytes: Long = 0,
        txBytes: Long = 0
    ) {
        val entity = ConnectionLogEntity(
            packageName = packageName,
            appName = appName ?: packageName ?: "System/Unknown",
            uid = uid,
            srcIp = srcIp,
            srcPort = srcPort,
            dstIp = dstIp,
            dstPort = dstPort,
            protocol = protocol,
            status = if (isAllowed) "ALLOWED" else "BLOCKED",
            reason = reason,
            rxBytes = rxBytes,
            txBytes = txBytes,
            timestamp = System.currentTimeMillis()
        )
        logChannel.trySend(entity)
    }

    suspend fun clearLogs() {
        dao.clearLogs()
    }
}
