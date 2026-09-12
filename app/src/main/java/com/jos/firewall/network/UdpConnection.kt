package com.jos.firewall.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Tracks and relays userspace UDP sessions (including DNS traffic).
 */
class UdpConnection(
    val srcIp: InetAddress,
    val srcPort: Int,
    val dstIp: InetAddress,
    val dstPort: Int,
    val uid: Int
) {
    var socket: DatagramSocket? = null
    var lastActiveTime: Long = System.currentTimeMillis()
    var isBlocked: Boolean = false

    fun isTimedOut(timeoutMs: Long = 30_000): Boolean {
        return (System.currentTimeMillis() - lastActiveTime) > timeoutMs
    }

    fun updateActivity() {
        lastActiveTime = System.currentTimeMillis()
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }

    /**
     * Builds an IPv4 UDP packet containing response data from a protected socket.
     */
    fun createResponsePacket(payload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0.toShort())
        packet.putShort(0x4000.toShort())
        packet.put(64.toByte())
        packet.put(17.toByte()) // UDP
        packet.putShort(0.toShort())

        // Swap src and dst for return
        packet.put(dstIp.address)
        packet.put(srcIp.address)

        val ipChecksum = IPv4Packet.computeChecksum(packet, 0, 20)
        packet.putShort(10, ipChecksum)

        // UDP Header
        packet.position(20)
        packet.putShort(dstPort.toShort())
        packet.putShort(srcPort.toShort())
        packet.putShort((8 + payload.size).toShort())
        packet.putShort(0.toShort()) // UDP Checksum optional in IPv4

        // UDP Payload
        packet.put(payload)

        return packet.array()
    }
}
