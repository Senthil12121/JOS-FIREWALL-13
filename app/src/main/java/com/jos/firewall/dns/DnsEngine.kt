package com.jos.firewall.dns

import com.jos.firewall.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance DNS inspection, filtering, and sinkhole engine.
 */
class DnsEngine(
    private val database: AppDatabase,
    private val scope: CoroutineScope,
    private val context: android.content.Context? = null
) {
    var upstreamDnsAddress: InetAddress = InetAddress.getByName("1.1.1.1")
    private val blockedDomains = ConcurrentHashMap<String, Boolean>()
    private val dao = database.firewallDao()

    init {
        scope.launch(Dispatchers.IO) {
            dao.getAllDnsRulesFlow().collectLatest { rules ->
                blockedDomains.clear()
                for (rule in rules) {
                    if (rule.isBlocked) {
                        blockedDomains[rule.domain.lowercase()] = true
                    }
                }
            }
        }

        if (context != null) {
            scope.launch(Dispatchers.IO) {
                DnsProviderManager.getInstance(context).currentProviderFlow.collectLatest { provider ->
                    try {
                        upstreamDnsAddress = InetAddress.getByName(provider.primaryIp)
                    } catch (_: Exception) {
                        // Keep previous upstream if the address fails to resolve
                    }
                }
            }
        }
    }

    /**
     * Inspects a UDP DNS query payload.
     * Returns:
     * - A sinkhole response ByteArray (0.0.0.0) if the domain is blocked
     * - Null if the query is allowed and should proceed upstream
     */
    fun processDnsQuery(queryPayload: ByteArray): ByteArray? {
        if (queryPayload.size < 12) return null // DNS header is at least 12 bytes

        val domain = extractDomainFromQuery(queryPayload) ?: return null

        if (isDomainBlocked(domain)) {
            // Record hit count asynchronously
            scope.launch(Dispatchers.IO) {
                dao.incrementDnsHit(domain)
            }
            return synthesizeSinkholeResponse(queryPayload, domain)
        }

        return null
    }

    fun isDomainBlocked(domain: String): Boolean {
        val cleanDomain = domain.lowercase().trim('.')
        if (blockedDomains.containsKey(cleanDomain)) return true

        // Check wildcard subdomains (e.g. sub.doubleclick.net matches doubleclick.net)
        for (blocked in blockedDomains.keys) {
            if (cleanDomain.endsWith(".$blocked")) {
                return true
            }
        }
        return false
    }

    /**
     * Parses standard DNS Question QNAME from query buffer.
     */
    fun extractDomainFromQuery(payload: ByteArray): String? {
        try {
            var pos = 12 // Skip 12-byte header
            val sb = StringBuilder()

            while (pos < payload.size) {
                val labelLength = payload[pos].toInt() and 0xFF
                if (labelLength == 0) break // Root label reached
                pos++

                if (pos + labelLength > payload.size) return null

                if (sb.isNotEmpty()) sb.append(".")
                for (i in 0 until labelLength) {
                    sb.append(payload[pos + i].toInt().toChar())
                }
                pos += labelLength
            }
            return sb.toString()
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Forges a DNS Response packet pointing the domain to 0.0.0.0 (Sinkhole).
     */
    private fun synthesizeSinkholeResponse(queryPayload: ByteArray, domain: String): ByteArray {
        val queryLength = queryPayload.size
        // Original query + 16 bytes for Answer RR (Name pointer [2] + Type A [2] + Class IN [2] + TTL [4] + DataLen [2] + 0.0.0.0 [4])
        val response = ByteBuffer.allocate(queryLength + 16)

        // Copy original query
        response.put(queryPayload)

        // Modify DNS Flags (Bytes 2 & 3)
        // QR = 1 (Response), AA = 1, RD = 1, RA = 1, RCODE = 0 (No error, returns 0.0.0.0)
        response.put(2, 0x81.toByte())
        response.put(3, 0x80.toByte())

        // Set ANCOUNT (Answer Count) to 1 (Bytes 6 & 7)
        response.putShort(6, 1.toShort())

        // Append Answer Record at the end
        response.position(queryLength)
        response.putShort(0xC00C.toShort()) // Name pointer to QNAME at offset 12
        response.putShort(0x0001.toShort()) // Type A
        response.putShort(0x0001.toShort()) // Class IN
        response.putInt(300) // TTL 300 seconds
        response.putShort(4.toShort()) // Data length = 4 bytes for IPv4
        response.put(byteArrayOf(0, 0, 0, 0)) // 0.0.0.0 Sinkhole IP

        return response.array()
    }
}
