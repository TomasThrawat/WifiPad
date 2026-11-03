package com.wifipad.receiver

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SecureSessionTrackerTest {
    private val phoneA = InetAddress.getByName("192.0.2.10")
    private val phoneB = InetAddress.getByName("192.0.2.11")
    private fun tracker() = SecureSessionTracker(750_000_000L, 1_200_000_000L, 64)

    @Test fun pendingHandshakeIsCommittedOnlyByAuthenticatedFreshSessionInput() {
        val tracker = tracker()
        val pending = tracker.acceptHello(phoneA, 41000, 101L, 201L, 1_000L)
        assertNotNull(pending)
        assertSame(pending, tracker.acceptHello(phoneA, 41000, 101L, 999L, 2_000L))
        assertEquals(SecureSessionTracker.InputResult.PROMOTED, tracker.acceptInput(pending!!, 0L, 3_000L))
        assertEquals(SecureSessionTracker.InputResult.ACCEPTED, tracker.acceptInput(pending, 1L, 4_000L))
        assertEquals(SecureSessionTracker.InputResult.REJECTED, tracker.acceptInput(pending, 1L, 5_000L))
    }

    @Test fun aNewHelloCannotReplaceARecentlyActiveController() {
        val tracker = tracker()
        val active = tracker.acceptHello(phoneA, 41000, 101L, 201L, 1_000_000_000L)!!
        assertEquals(SecureSessionTracker.InputResult.PROMOTED, tracker.acceptInput(active, 0L, 1_000_000_100L))
        assertNull(tracker.acceptHello(phoneB, 42000, 102L, 202L, 1_500_000_000L))
        assertEquals(SecureSessionTracker.InputResult.ACCEPTED, tracker.acceptInput(active, 1L, 1_550_000_000L))
        assertNull(tracker.acceptHello(phoneB, 42000, 103L, 203L, 2_000_000_000L))
    }

    @Test fun anOldControllerCanResumeBeforeAProvisionalReplacementIsCommitted() {
        val tracker = tracker()
        val oldSession = tracker.acceptHello(phoneA, 41000, 101L, 201L, 1_000_000_000L)!!
        tracker.acceptInput(oldSession, 0L, 1_000_000_000L)
        val replacement = tracker.acceptHello(phoneB, 42000, 102L, 202L, 1_800_000_001L)!!

        assertEquals(SecureSessionTracker.InputResult.ACCEPTED, tracker.acceptInput(oldSession, 1L, 1_850_000_000L))
        assertEquals(SecureSessionTracker.InputResult.REJECTED, tracker.acceptInput(replacement, 0L, 1_850_000_001L))
        assertEquals(SecureSessionTracker.InputResult.PROMOTED, tracker.acceptInput(replacement, 1L, 2_600_000_001L))
        assertEquals(SecureSessionTracker.InputResult.REJECTED, tracker.acceptInput(oldSession, 2L, 2_600_000_002L))
    }

    @Test fun recentlyUsedClientNoncesAreRejectedEvenAfterPendingHandshakeExpires() {
        val tracker = tracker()
        assertNotNull(tracker.acceptHello(phoneA, 41000, 101L, 201L, 1_000L))
        assertNull(tracker.acceptHello(phoneA, 41000, 101L, 202L, 2_000_000_000L))
        assertNotNull(tracker.acceptHello(phoneA, 41000, 102L, 203L, 2_000_000_001L))
    }

    @Test fun aFreshActiveControllerPreventsASecondControllerHandshake() {
        val tracker = tracker()
        val oldSession = tracker.acceptHello(phoneA, 41000, 101L, 201L, 1_000_000_000L)!!
        tracker.acceptInput(oldSession, 0L, 1_000_000_000L)
        assertNull(tracker.acceptHello(phoneB, 42000, 102L, 202L, 1_500_000_000L))
        assertEquals(SecureSessionTracker.InputResult.ACCEPTED, tracker.acceptInput(oldSession, 1L, 1_550_000_000L))
    }
    @Test fun newHandshakeNoncesAreRateLimitedButTheWindowResets() {
        val tracker = tracker()
        val interval = 1_200_000_001L
        repeat(8) { index ->
            val now = index * interval
            assertNotNull(tracker.acceptHello(phoneA, 41000, (100L + index), (200L + index), now))
        }
        assertNull(tracker.acceptHello(phoneA, 41000, 999L, 1_999L, 9_600_000_008L))
        assertNotNull(tracker.acceptHello(phoneA, 41000, 1_000L, 2_000L, 10_000_000_000L))
    }

}
