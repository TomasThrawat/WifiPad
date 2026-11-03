package com.wifipad.controller

import java.util.LinkedHashMap

/** Matches authenticated ACK sequence numbers to the exact time each input frame was sent. */
internal class AckLedger(private val capacity: Int = 128) {
    private val sentAtBySequence = LinkedHashMap<Long, Long>()

    init {
        require(capacity > 0)
    }

    fun recordSent(sequence: Long, sentAtNanos: Long) {
        require(sequence >= 0L)
        sentAtBySequence[sequence] = sentAtNanos
        while (sentAtBySequence.size > capacity) {
            sentAtBySequence.remove(sentAtBySequence.keys.first())
        }
    }

    /** Returns the non-negative RTT in nanoseconds, or null for unknown/duplicate ACKs. */
    fun acknowledge(sequence: Long, acknowledgedAtNanos: Long): Long? {
        if (sequence < 0L) return null
        val sentAt = sentAtBySequence.remove(sequence) ?: return null
        return (acknowledgedAtNanos - sentAt).coerceAtLeast(0L)
    }

    fun clear() = sentAtBySequence.clear()
}
