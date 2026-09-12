package com.jos.firewall.network

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Tracks individual TCP 5-tuple sessions through their lifecycle states.
 */
class TcpConnection(
    val srcIp: InetAddress,
    val srcPort: Int,
    val dstIp: InetAddress,
    val dstPort: Int,
    val uid: Int
) {
    enum class State {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        FIN_WAIT,
        CLOSE_WAIT,
        CLOSED
    }

    var state: State = State.SYN_SENT
    var mySequenceNum: Long = 1000L
    var theirSequenceNum: Long = 0L
    var lastActiveTime: Long = System.currentTimeMillis()
    var isBlocked: Boolean = false

    fun isTimedOut(timeoutMs: Long = 60_000): Boolean {
        return (System.currentTimeMillis() - lastActiveTime) > timeoutMs
    }

    fun updateActivity() {
        lastActiveTime = System.currentTimeMillis()
    }

    /**
     * Synthesizes an IPv4 TCP RST packet to cleanly terminate blocked or refused connections.
     */
    fun createRstPacket(isIpv6: Boolean = false): ByteArray {
        val totalLength = 40 // 20 bytes IPv4 + 20 bytes TCP
        val packet = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        packet.put(0x45.toByte()) // Version 4, IHL 5
        packet.put(0x00.toByte()) // DSCP
        packet.putShort(totalLength.toShort()) // Total length
        packet.putShort(0.toShort()) // Identification
        packet.putShort(0x4000.toShort()) // Flags (DF)
        packet.put(64.toByte()) // TTL
        packet.put(6.toByte()) // Protocol (TCP)
        packet.putShort(0.toShort()) // Checksum placeholder

        // Swap src and dst for reply
        packet.put(dstIp.address)
        packet.put(srcIp.address)

        // Calculate IPv4 Checksum
        val ipChecksum = IPv4Packet.computeChecksum(packet, 0, 20)
        packet.putShort(10, ipChecksum)

        // TCP Header
        val tcpOffset = 20
        packet.position(tcpOffset)
        packet.putShort(dstPort.toShort()) // Source port (reply from dest)
        packet.putShort(srcPort.toShort()) // Destination port
        packet.putInt(0) // Sequence number
        packet.putInt((theirSequenceNum + 1).toInt()) // Acknowledgment number
        packet.put(0x50.toByte()) // Data offset (5 * 4 = 20 bytes)
        packet.put(0x14.toByte()) // Flags: RST + ACK (0x04 | 0x10)
        packet.putShort(0) // Window size
        packet.putShort(0) // Checksum placeholder
        packet.putShort(0) // Urgent pointer

        return packet.array()
    }
}
