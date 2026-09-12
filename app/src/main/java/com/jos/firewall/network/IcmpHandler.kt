package com.jos.firewall.network

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Handles ICMP (Ping / Echo Request & Unreachable) packet generation and filtering.
 */
class IcmpHandler {

    /**
     * Builds an ICMP Destination Unreachable / Port Unreachable reply packet
     */
    fun createDestinationUnreachable(
        srcIp: InetAddress,
        dstIp: InetAddress,
        originalIpHeaderAndData: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + originalIpHeaderAndData.size
        val packet = ByteBuffer.allocate(totalLength)

        // IPv4 Header
        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0.toShort())
        packet.put(64.toByte())
        packet.put(1.toByte()) // ICMP Protocol = 1
        packet.putShort(0.toShort())

        // Reply from destination to source
        packet.put(dstIp.address)
        packet.put(srcIp.address)

        val ipChecksum = IPv4Packet.computeChecksum(packet, 0, 20)
        packet.putShort(10, ipChecksum)

        // ICMP Header
        packet.position(20)
        packet.put(3.toByte()) // Type 3: Destination Unreachable
        packet.put(3.toByte()) // Code 3: Port Unreachable
        packet.putShort(0.toShort()) // Checksum
        packet.putInt(0) // Unused 4 bytes

        packet.put(originalIpHeaderAndData)

        val icmpChecksum = IPv4Packet.computeChecksum(packet, 20, 8 + originalIpHeaderAndData.size)
        packet.putShort(22, icmpChecksum)

        return packet.array()
    }
}
