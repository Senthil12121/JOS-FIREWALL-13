package com.jos.firewall.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import com.jos.firewall.dns.DnsEngine
import com.jos.firewall.firewall.FirewallEngine
import com.jos.firewall.logging.PacketLogger
import com.jos.firewall.network.*
import com.jos.firewall.stats.TrafficStatsManager
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Core userspace networking pipeline that processes packets arriving from the TUN interface.
 */
class VpnEngine(
    private val vpnService: FirewallVpnService,
    private val vpnInterface: ParcelFileDescriptor,
    private val firewallEngine: FirewallEngine,
    private val dnsEngine: DnsEngine,
    private val packetLogger: PacketLogger,
    private val statsManager: TrafficStatsManager
) {
    private val connectionTracker = ConnectionTracker()
    private val icmpHandler = IcmpHandler()
    private var packetReader: PacketReader? = null
    private var packetWriter: PacketWriter? = null

    @Volatile
    private var isRunning = false
    private var workerJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning) return
        isRunning = true

        val fd = vpnInterface.fileDescriptor
        packetReader = PacketReader(fd)
        packetWriter = PacketWriter(fd)

        // Periodic maintenance job (cleans timed out connections)
        engineScope.launch {
            while (isRunning) {
                delay(15_000)
                connectionTracker.cleanUpTimeouts()
            }
        }

        // Main packet processing loop
        workerJob = engineScope.launch {
            val packetBuffer = ByteBuffer.allocate(32767)

            while (isRunning && isActive) {
                val bytesRead = packetReader?.readPacket(packetBuffer) ?: -1
                if (bytesRead > 0) {
                    processOutboundPacket(packetBuffer, bytesRead)
                } else if (bytesRead < 0) {
                    break
                }
            }
        }
    }

    private fun processOutboundPacket(buffer: ByteBuffer, length: Int) {
        val version = (buffer.get(0).toInt() shr 4) and 0x0F

        if (version == 4) {
            handleIPv4Packet(buffer, length)
        } else if (version == 6) {
            handleIPv6Packet(buffer, length)
        }
    }

    private fun handleIPv4Packet(buffer: ByteBuffer, length: Int) {
        try {
            val ipPacket = IPv4Packet(buffer)
            val isWifi = isCurrentNetworkWifi()

            when {
                ipPacket.isUdp() -> handleUdpIPv4(ipPacket, isWifi)
                ipPacket.isTcp() -> handleTcpIPv4(ipPacket, isWifi)
                ipPacket.isIcmp() -> handleIcmpIPv4(ipPacket, isWifi)
                else -> {
                    // Other protocols passed or dropped
                    statsManager.recordAllowed(0, length.toLong())
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleUdpIPv4(ipPacket: IPv4Packet, isWifi: Boolean) {
        val buffer = ipPacket.rawBuffer
        val offset = ipPacket.transportOffset
        buffer.position(offset)

        val srcPort = buffer.short.toInt() and 0xFFFF
        val dstPort = buffer.short.toInt() and 0xFFFF
        val udpLength = buffer.short.toInt() and 0xFFFF
        buffer.short // Skip checksum

        val payloadSize = udpLength - 8
        val payload = ByteArray(payloadSize.coerceAtLeast(0))
        if (payloadSize > 0 && buffer.remaining() >= payloadSize) {
            buffer.get(payload)
        }

        val uid = resolveUid(ipPacket.sourceAddress, srcPort, ipPacket.destinationAddress, dstPort, "UDP")
        val pkgName = resolvePackageName(uid)

        // Evaluate Firewall Policy
        val decision = firewallEngine.evaluate(
            srcIp = ipPacket.sourceAddress,
            srcPort = srcPort,
            dstIp = ipPacket.destinationAddress,
            dstPort = dstPort,
            protocol = "UDP",
            uid = uid,
            packageName = pkgName,
            isWifi = isWifi
        )

        packetLogger.logConnection(
            packageName = pkgName,
            appName = pkgName,
            uid = uid,
            srcIp = ipPacket.sourceAddress.hostAddress ?: "unknown",
            srcPort = srcPort,
            dstIp = ipPacket.destinationAddress.hostAddress ?: "unknown",
            dstPort = dstPort,
            protocol = "UDP",
            isAllowed = decision.isAllowed,
            reason = decision.reason,
            txBytes = ipPacket.totalLength.toLong()
        )

        if (!decision.isAllowed) {
            statsManager.recordBlocked(ipPacket.totalLength.toLong())
            return // Drop packet
        }

        statsManager.recordAllowed(0, ipPacket.totalLength.toLong())

        // Check if DNS Query (Destination Port 53)
        if (dstPort == 53) {
            val sinkholeResponse = dnsEngine.processDnsQuery(payload)
            if (sinkholeResponse != null) {
                // Return synthesized 0.0.0.0 sinkhole response to TUN
                val conn = connectionTracker.getOrCreateUdp(
                    ipPacket.sourceAddress, srcPort,
                    ipPacket.destinationAddress, dstPort, uid
                )
                val responsePacket = conn.createResponsePacket(sinkholeResponse)
                packetWriter?.writePacket(responsePacket)
                statsManager.recordBlocked(responsePacket.size.toLong())
                return
            }
        }

        // Forward allowed UDP packet through userspace protected socket
        forwardUdpUserspace(ipPacket.sourceAddress, srcPort, ipPacket.destinationAddress, dstPort, uid, payload)
    }

    private fun forwardUdpUserspace(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        uid: Int,
        payload: ByteArray
    ) {
        val conn = connectionTracker.getOrCreateUdp(srcIp, srcPort, dstIp, dstPort, uid)
        conn.updateActivity()

        engineScope.launch {
            try {
                if (conn.socket == null || conn.socket?.isClosed == true) {
                    val socket = DatagramSocket()
                    vpnService.protect(socket) // CRITICAL: Prevent routing loops back into TUN!
                    socket.soTimeout = 5000
                    conn.socket = socket

                    // Background listener for inbound response datagrams
                    launch {
                        val rxBuffer = ByteArray(4096)
                        val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                        while (isRunning && conn.socket != null && !conn.socket!!.isClosed) {
                            try {
                                conn.socket?.receive(rxPacket)
                                val responseData = rxPacket.data.copyOf(rxPacket.length)
                                val tunPacket = conn.createResponsePacket(responseData)
                                packetWriter?.writePacket(tunPacket)
                                statsManager.recordAllowed(tunPacket.size.toLong(), 0)
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                }

                val outPacket = DatagramPacket(payload, payload.size, dstIp, dstPort)
                conn.socket?.send(outPacket)
            } catch (_: Exception) {}
        }
    }

    private fun handleTcpIPv4(ipPacket: IPv4Packet, isWifi: Boolean) {
        val buffer = ipPacket.rawBuffer
        val offset = ipPacket.transportOffset
        buffer.position(offset)

        val srcPort = buffer.short.toInt() and 0xFFFF
        val dstPort = buffer.short.toInt() and 0xFFFF
        val seq = buffer.int.toLong() and 0xFFFFFFFFL
        val ack = buffer.int.toLong() and 0xFFFFFFFFL
        val flagsByte = buffer.get(offset + 13).toInt() and 0xFF
        val isSyn = (flagsByte and 0x02) != 0

        val uid = resolveUid(ipPacket.sourceAddress, srcPort, ipPacket.destinationAddress, dstPort, "TCP")
        val pkgName = resolvePackageName(uid)

        val decision = firewallEngine.evaluate(
            srcIp = ipPacket.sourceAddress,
            srcPort = srcPort,
            dstIp = ipPacket.destinationAddress,
            dstPort = dstPort,
            protocol = "TCP",
            uid = uid,
            packageName = pkgName,
            isWifi = isWifi
        )

        val conn = connectionTracker.getOrCreateTcp(
            ipPacket.sourceAddress, srcPort,
            ipPacket.destinationAddress, dstPort, uid
        )
        conn.theirSequenceNum = seq
        conn.updateActivity()

        if (isSyn) {
            packetLogger.logConnection(
                packageName = pkgName,
                appName = pkgName,
                uid = uid,
                srcIp = ipPacket.sourceAddress.hostAddress ?: "unknown",
                srcPort = srcPort,
                dstIp = ipPacket.destinationAddress.hostAddress ?: "unknown",
                dstPort = dstPort,
                protocol = "TCP",
                isAllowed = decision.isAllowed,
                reason = decision.reason,
                txBytes = ipPacket.totalLength.toLong()
            )
        }

        if (!decision.isAllowed) {
            statsManager.recordBlocked(ipPacket.totalLength.toLong())
            // Fast failure: Send TCP RST back to TUN so connection closes cleanly and instantly
            val rstPacket = conn.createRstPacket(isIpv6 = false)
            packetWriter?.writePacket(rstPacket)
            connectionTracker.removeTcp(ipPacket.sourceAddress, srcPort, ipPacket.destinationAddress, dstPort)
            return
        }

        statsManager.recordAllowed(0, ipPacket.totalLength.toLong())
    }

    private fun handleIcmpIPv4(ipPacket: IPv4Packet, isWifi: Boolean) {
        val uid = 0
        val decision = firewallEngine.evaluate(
            srcIp = ipPacket.sourceAddress,
            srcPort = 0,
            dstIp = ipPacket.destinationAddress,
            dstPort = 0,
            protocol = "ICMP",
            uid = uid,
            packageName = null,
            isWifi = isWifi
        )

        packetLogger.logConnection(
            packageName = null,
            appName = "ICMP Echo",
            uid = uid,
            srcIp = ipPacket.sourceAddress.hostAddress ?: "",
            srcPort = 0,
            dstIp = ipPacket.destinationAddress.hostAddress ?: "",
            dstPort = 0,
            protocol = "ICMP",
            isAllowed = decision.isAllowed,
            reason = decision.reason,
            txBytes = ipPacket.totalLength.toLong()
        )

        if (decision.isAllowed) {
            statsManager.recordAllowed(0, ipPacket.totalLength.toLong())
        } else {
            statsManager.recordBlocked(ipPacket.totalLength.toLong())
        }
    }

    private fun handleIPv6Packet(buffer: ByteBuffer, length: Int) {
        try {
            val ip6 = IPv6Packet(buffer)
            val isWifi = isCurrentNetworkWifi()
            val decision = firewallEngine.evaluate(
                srcIp = ip6.sourceAddress,
                srcPort = 0,
                dstIp = ip6.destinationAddress,
                dstPort = 0,
                protocol = if (ip6.isTcp()) "TCP" else if (ip6.isUdp()) "UDP" else "IPv6",
                uid = 0,
                packageName = null,
                isWifi = isWifi
            )

            if (decision.isAllowed) {
                statsManager.recordAllowed(0, length.toLong())
            } else {
                statsManager.recordBlocked(length.toLong())
            }
        } catch (_: Exception) {}
    }

    private fun resolveUid(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        protocol: String
    ): Int {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val cm = vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val protoInt = if (protocol == "TCP") 6 else 17
                val inetSocketAddressSrc = java.net.InetSocketAddress(srcIp, srcPort)
                val inetSocketAddressDst = java.net.InetSocketAddress(dstIp, dstPort)
                cm.getConnectionOwnerUid(protoInt, inetSocketAddressSrc, inetSocketAddressDst)
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun resolvePackageName(uid: Int): String? {
        if (uid <= 0) return null
        return try {
            vpnService.packageManager.getNameForUid(uid)
        } catch (_: Exception) {
            null
        }
    }

    private fun isCurrentNetworkWifi(): Boolean {
        return try {
            val cm = vpnService.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            true
        }
    }

    fun stop() {
        isRunning = false
        workerJob?.cancel()
        engineScope.cancel()
        packetReader?.close()
        packetWriter?.close()
        connectionTracker.clearAll()
        try {
            vpnInterface.close()
        } catch (_: Exception) {}
    }
}
