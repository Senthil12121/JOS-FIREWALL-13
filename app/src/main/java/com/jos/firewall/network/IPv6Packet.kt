package com.jos.firewall.network

import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Parser and container for IPv6 packets read from the TUN interface.
 */
class IPv6Packet(val rawBuffer: ByteBuffer) {

    val version: Int
    val trafficClass: Int
    val flowLabel: Int
    val payloadLength: Int
    val nextHeader: Int
    val hopLimit: Int
    val sourceAddress: InetAddress
    val destinationAddress: InetAddress

    val transportOffset: Int = 40 // Fixed IPv6 base header is 40 bytes
    val transportLength: Int

    init {
        val originalPosition = rawBuffer.position()
        rawBuffer.position(0)

        val firstWord = rawBuffer.int
        version = (firstWord shr 28) and 0x0F
        trafficClass = (firstWord shr 20) and 0xFF
        flowLabel = firstWord and 0xFFFFF

        payloadLength = rawBuffer.short.toInt() and 0xFFFF
        nextHeader = rawBuffer.get().toInt() and 0xFF
        hopLimit = rawBuffer.get().toInt() and 0xFF

        val srcBytes = ByteArray(16)
        rawBuffer.get(srcBytes)
        sourceAddress = Inet6Address.getByAddress(srcBytes)

        val dstBytes = ByteArray(16)
        rawBuffer.get(dstBytes)
        destinationAddress = Inet6Address.getByAddress(dstBytes)

        transportLength = payloadLength

        rawBuffer.position(originalPosition)
    }

    fun isTcp(): Boolean = nextHeader == 6
    fun isUdp(): Boolean = nextHeader == 17
    fun isIcmpV6(): Boolean = nextHeader == 58
}
