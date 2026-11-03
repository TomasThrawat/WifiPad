package com.wifipad.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AckLedgerTest {
    @Test fun delayedAcknowledgementsUseTheirOwnPacketSendTimestamp() {
        val ledger = AckLedger()
        ledger.recordSent(10L, 1_000L)
        ledger.recordSent(11L, 5_000L)

        assertEquals(9_000L, ledger.acknowledge(10L, 10_000L))
        assertEquals(6_000L, ledger.acknowledge(11L, 11_000L))
    }

    @Test fun duplicateAndUnknownAcknowledgementsAreIgnored() {
        val ledger = AckLedger()
        ledger.recordSent(7L, 100L)

        assertEquals(25L, ledger.acknowledge(7L, 125L))
        assertNull(ledger.acknowledge(7L, 150L))
        assertNull(ledger.acknowledge(99L, 200L))
        assertNull(ledger.acknowledge(-1L, 200L))
    }

    @Test fun oldPendingTimestampsAreBounded() {
        val ledger = AckLedger(capacity = 2)
        ledger.recordSent(1L, 100L)
        ledger.recordSent(2L, 200L)
        ledger.recordSent(3L, 300L)

        assertNull(ledger.acknowledge(1L, 400L))
        assertEquals(200L, ledger.acknowledge(2L, 400L))
        assertEquals(100L, ledger.acknowledge(3L, 400L))
    }
}
