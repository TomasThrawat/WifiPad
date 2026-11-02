package com.wifipad.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerMappingTest {
    @Test fun usesAndroidGenericKeyLayoutCodes() {
        assertEquals(10, TriggerMapping.ABS_BRAKE)
        assertEquals(9, TriggerMapping.ABS_GAS)
        assertEquals(312, TriggerMapping.BTN_TL2)
        assertEquals(313, TriggerMapping.BTN_TR2)
    }

    @Test fun digitalTriggerThresholdIsHalfRange() {
        assertFalse(TriggerMapping.isPressed(0))
        assertFalse(TriggerMapping.isPressed(127))
        assertTrue(TriggerMapping.isPressed(128))
        assertTrue(TriggerMapping.isPressed(255))
    }
}
