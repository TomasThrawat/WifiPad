package com.wifipad.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class DpadHatTest {
    @Test
    fun mapsAllEightDirectionsAndNeutral() {
        assertEquals(0 to 0, DpadHat.fromWireValue(0))
        assertEquals(0 to -1, DpadHat.fromWireValue(1))
        assertEquals(1 to -1, DpadHat.fromWireValue(2))
        assertEquals(1 to 0, DpadHat.fromWireValue(3))
        assertEquals(1 to 1, DpadHat.fromWireValue(4))
        assertEquals(0 to 1, DpadHat.fromWireValue(5))
        assertEquals(-1 to 1, DpadHat.fromWireValue(6))
        assertEquals(-1 to 0, DpadHat.fromWireValue(7))
        assertEquals(-1 to -1, DpadHat.fromWireValue(8))
        assertEquals(0 to 0, DpadHat.fromWireValue(255))
    }
}
