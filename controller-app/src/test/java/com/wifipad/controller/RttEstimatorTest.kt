package com.wifipad.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RttEstimatorTest {
    @Test
    fun reportsNoLatencyBeforeTheFirstAcknowledgement() {
        val estimator = RttEstimator()
        assertNull(estimator.roundTripMillis())
        assertTrue(estimator.addSample(12_000_000L))
        assertEquals(12L, estimator.roundTripMillis())
    }

    @Test
    fun smoothsShortTermRttChanges() {
        val estimator = RttEstimator()
        estimator.addSample(4_000_000L)
        assertFalse(estimator.addSample(20_000_000L))
        assertEquals(6L, estimator.roundTripMillis())
    }

    @Test
    fun roundsSubMillisecondMeasurementsUpToOneMillisecond() {
        val estimator = RttEstimator()
        estimator.addSample(250_000L)
        assertEquals(1L, estimator.roundTripMillis())
    }

    @Test
    fun resetClearsTheOldConnectionMeasurement() {
        val estimator = RttEstimator()
        estimator.addSample(8_000_000L)
        estimator.reset()
        assertNull(estimator.roundTripMillis())
        assertTrue(estimator.addSample(3_000_000L))
        assertEquals(3L, estimator.roundTripMillis())
    }
}
