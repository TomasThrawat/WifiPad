package com.wifipad.controller

/** Smooths authenticated input-to-ACK round-trip samples without hiding reconnects. */
internal class RttEstimator {
    private var estimateNanos: Long? = null

    /** Returns true for the first sample after construction or reset. */
    @Synchronized
    fun addSample(sampleNanos: Long): Boolean {
        require(sampleNanos >= 0L)
        val previous = estimateNanos
        estimateNanos = if (previous == null) {
            sampleNanos
        } else {
            previous + (sampleNanos - previous) / 8L
        }
        return previous == null
    }

    @Synchronized
    fun roundTripMillis(): Long? = estimateNanos?.let { ((it + 999_999L) / 1_000_000L).coerceAtLeast(1L) }

    @Synchronized
    fun reset() {
        estimateNanos = null
    }
}
