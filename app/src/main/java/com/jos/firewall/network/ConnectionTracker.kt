package com.jos.firewall.network

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry and lifecycle manager for all active IP flows.
 */
class ConnectionTracker {

    private val tcpTable = ConcurrentHashMap<String, TcpConnection>()
    private val udpTable = ConcurrentHashMap<String, UdpConnection>()

    private fun buildKey(srcIp: InetAddress, srcPort: Int, dstIp: InetAddress, dstPort: Int): String {
        return "${srcIp.hostAddress}:$srcPort->${dstIp.hostAddress}:$dstPort"
    }

    fun getOrCreateTcp(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        uid: Int
    ): TcpConnection {
        val key = buildKey(srcIp, srcPort, dstIp, dstPort)
        return tcpTable.computeIfAbsent(key) {
            TcpConnection(srcIp, srcPort, dstIp, dstPort, uid)
        }
    }

    fun getOrCreateUdp(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        uid: Int
    ): UdpConnection {
        val key = buildKey(srcIp, srcPort, dstIp, dstPort)
        return udpTable.computeIfAbsent(key) {
            UdpConnection(srcIp, srcPort, dstIp, dstPort, uid)
        }
    }

    fun removeTcp(srcIp: InetAddress, srcPort: Int, dstIp: InetAddress, dstPort: Int) {
        val key = buildKey(srcIp, srcPort, dstIp, dstPort)
        tcpTable.remove(key)
    }

    fun removeUdp(srcIp: InetAddress, srcPort: Int, dstIp: InetAddress, dstPort: Int) {
        val key = buildKey(srcIp, srcPort, dstIp, dstPort)
        udpTable.remove(key)?.close()
    }

    /**
     * Purges stale or timed-out connections from NAT tracking tables.
     */
    fun cleanUpTimeouts() {
        val tcpIter = tcpTable.entries.iterator()
        while (tcpIter.hasNext()) {
            val entry = tcpIter.next()
            if (entry.value.isTimedOut()) {
                tcpIter.remove()
            }
        }

        val udpIter = udpTable.entries.iterator()
        while (udpIter.hasNext()) {
            val entry = udpIter.next()
            if (entry.value.isTimedOut()) {
                entry.value.close()
                udpIter.remove()
            }
        }
    }

    fun clearAll() {
        for (udp in udpTable.values) {
            udp.close()
        }
        udpTable.clear()
        tcpTable.clear()
    }
}
