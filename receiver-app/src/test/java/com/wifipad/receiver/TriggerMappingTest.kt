package com.wifipad.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerMappingTest {
    @Test fun usesAospXbox360VendorKeyLayoutTriggerAxes() {
        assertEquals(2, TriggerMapping.ABS_Z)
        assertEquals(5, TriggerMapping.ABS_RZ)
    }
}
