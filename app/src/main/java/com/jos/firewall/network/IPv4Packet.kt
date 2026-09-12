package com.jos.firewall.network

import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * High-performance parser and container for IPv4 packets read from the TUN interface.
 */
class IPv4Packet(val rawBuffer: ByteBuffer) {

    val version: Int
    val headerLength: Int
    val typeOfService: Int
    val totalLength: Int
    val identification: Int
    val flags: Int
    val fragmentOffset: Int
    val ttl: Int
    val protocol: Int
    val headerChecksum: Int
    val sourceAddress: InetAddress
    val destinationAddress: InetAddress

    // Transport payload offset inside rawBuffer
    val transportOffset: Int
    val transportLength: Int

    init {
        val originalPosition = rawBuffer.position()
        rawBuffer.position(0)

        val versionAndIHL = rawBuffer.get().toInt() and 0xFF
        version = (versionAndIHL shr 4) and 0x0F
        headerLength = (versionAndIHL and 0x0F) * 4

        typeOfService = rawBuffer.get().toInt() and 0xFF
        totalLength = rawBuffer.short.toInt() and 0xFFFF
        identification = rawBuffer.short.toInt() and 0xFFFF

        val flagsAndOffset = rawBuffer.short.toInt() and 0xFFFF
        flags = (flagsAndOffset shr 13) and 0x07
        fragmentOffset = flagsAndOffset and 0x1FFF

        ttl = rawBuffer.get().toInt() and 0xFF
        protocol = rawBuffer.get().toInt() and 0xFF
        headerChecksum = rawBuffer.short.toInt() and 0xFFFF

        val srcBytes = ByteArray(4)
        rawBuffer.get(srcBytes)
        sourceAddress = InetAddress.getByAddress(srcBytes)

        val dstBytes = ByteArray(4)
        rawBuffer.get(dstBytes)
        destinationAddress = InetAddress.getByAddress(dstBytes)

        transportOffset = headerLength
        transportLength = totalLength - headerLength

        // Restore buffer position
        rawBuffer.position(originalPosition)
    }

    fun isTcp(): Boolean = protocol == 6
    fun isUdp(): Boolean = protocol == 17
    fun isIcmp(): Boolean = protocol == 1

    companion object {
        fun computeChecksum(buffer: ByteBuffer, offset: Int, length: Int): Short {
            var sum = 0
            var i = offset
            val limit = offset + length - 1

            while (i < limit) {
                val word = ((buffer.get(i).toInt() and 0xFF) shl 8) or (buffer.get(i + 1).toInt() and 0xFF)
                sum += word
                i += 2
            }
            if (i == limit) {
                sum += (buffer.get(i).toInt() and 0xFF) shl 8
            }

            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return sum.inv().toShort()
        }
    }
}
