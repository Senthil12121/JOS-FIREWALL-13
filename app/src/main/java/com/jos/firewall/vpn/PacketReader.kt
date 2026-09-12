package com.jos.firewall.vpn

import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Continuous loop reading raw IP packets from the Android TUN file descriptor.
 */
class PacketReader(fileDescriptor: FileDescriptor) {

    private val inputStream = FileInputStream(fileDescriptor)

    /**
     * Reads a single IP packet from TUN interface into the provided buffer.
     * Returns the number of bytes read, or -1 on EOF/error.
     */
    fun readPacket(buffer: ByteBuffer): Int {
        return try {
            buffer.clear()
            val bytesRead = inputStream.read(buffer.array())
            if (bytesRead > 0) {
                buffer.limit(bytesRead)
            }
            bytesRead
        } catch (e: Exception) {
            -1
        }
    }

    fun close() {
        try {
            inputStream.close()
        } catch (_: Exception) {}
    }
}
