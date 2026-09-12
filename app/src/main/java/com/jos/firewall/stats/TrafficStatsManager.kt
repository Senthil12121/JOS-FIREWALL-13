package com.jos.firewall.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class TrafficStats(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val packetsAllowed: Long = 0,
    val packetsBlocked: Long = 0
) {
    val formattedDownload: String get() = formatBytes(bytesIn)
    val formattedUpload: String get() = formatBytes(bytesOut)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

/**
 * High-performance lock-free traffic statistics tracker.
 */
class TrafficStatsManager {

    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val packetsAllowed = AtomicLong(0)
    private val packetsBlocked = AtomicLong(0)

    private val _statsFlow = MutableStateFlow(TrafficStats())
    val statsFlow: StateFlow<TrafficStats> = _statsFlow.asStateFlow()

    fun recordAllowed(rxBytes: Long, txBytes: Long) {
        bytesIn.addAndGet(rxBytes)
        bytesOut.addAndGet(txBytes)
        packetsAllowed.incrementAndGet()
        updateFlow()
    }

    fun recordBlocked(txBytes: Long) {
        bytesOut.addAndGet(txBytes)
        packetsBlocked.incrementAndGet()
        updateFlow()
    }

    fun reset() {
        bytesIn.set(0)
        bytesOut.set(0)
        packetsAllowed.set(0)
        packetsBlocked.set(0)
        updateFlow()
    }

    private fun updateFlow() {
        _statsFlow.value = TrafficStats(
            bytesIn = bytesIn.get(),
            bytesOut = bytesOut.get(),
            packetsAllowed = packetsAllowed.get(),
            packetsBlocked = packetsBlocked.get()
        )
    }
}
