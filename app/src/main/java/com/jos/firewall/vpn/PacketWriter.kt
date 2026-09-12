package com.jos.firewall.vpn

import java.io.FileDescriptor
import java.io.FileOutputStream

/**
 * Thread-safe writer for injecting incoming or synthesized packets into the Android TUN interface.
 */
class PacketWriter(fileDescriptor: FileDescriptor) {

    private val outputStream = FileOutputStream(fileDescriptor)
    private val lock = Any()

    fun writePacket(packetData: ByteArray) {
        synchronized(lock) {
            try {
                outputStream.write(packetData)
                outputStream.flush()
            } catch (_: Exception) {}
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                outputStream.close()
            } catch (_: Exception) {}
        }
    }
}
