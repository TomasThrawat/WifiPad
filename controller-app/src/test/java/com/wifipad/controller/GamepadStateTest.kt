package com.wifipad.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadStateTest {

    @Test
    fun packetUsesProtocolLayoutAndUnsignedRanges() {
        val state = GamepadState().apply {
            buttons = ButtonBit.A or ButtonBit.MODE
            leftX = (-127).toByte()
            leftY = 127
            rightX = (-1).toByte()
            rightY = 1
            leftTrigger = 255
            rightTrigger = 300
            dpad = 8
        }

        val packet = state.toPacket()

        assertEquals(Protocol.PACKET_SIZE, packet.size)
        assertEquals(Protocol.MAGIC.toInt(), packet[0].toInt())
        assertEquals(Protocol.VERSION.toInt(), packet[1].toInt())
        assertEquals(0x01, packet[2].toInt() and 0xFF)
        assertEquals(0x04, packet[3].toInt() and 0xFF)
        assertEquals(-127, packet[4].toInt())
        assertEquals(127, packet[5].toInt())
        assertEquals(255, packet[8].toInt() and 0xFF)
        assertEquals(255, packet[9].toInt() and 0xFF)
        assertEquals(8, packet[10].toInt() and 0xFF)
    }

    @Test
    fun setButtonSetsAndClearsOnlyRequestedBit() {
        val state = GamepadState()

        state.setButton(ButtonBit.A, true)
        state.setButton(ButtonBit.MODE, true)
        state.setButton(ButtonBit.A, false)

        assertEquals(ButtonBit.MODE, state.buttons)
    }
}
