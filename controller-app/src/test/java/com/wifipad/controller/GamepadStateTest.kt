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
        assertEquals(-1, packet[6].toInt())
        assertEquals(1, packet[7].toInt())
        assertEquals(255, packet[8].toInt() and 0xFF)
        assertEquals(255, packet[9].toInt() and 0xFF)
        assertEquals(8, packet[10].toInt() and 0xFF)
    }

    @Test
    fun writePacketReusesCallerBuffer() {
        val state = GamepadState().apply {
            buttons = ButtonBit.B
            leftX = 42
            rightX = (-64).toByte()
            rightY = 64
            rightTrigger = 128
        }
        val buffer = ByteArray(Protocol.PACKET_SIZE) { 0x7F }
        state.writePacket(buffer)
        assertEquals(Protocol.MAGIC, buffer[0])
        assertEquals(Protocol.VERSION, buffer[1])
        assertEquals(0x02, buffer[2].toInt() and 0xFF)
        assertEquals(42, buffer[4].toInt())
        assertEquals(-64, buffer[6].toInt())
        assertEquals(64, buffer[7].toInt())
        assertEquals(128, buffer[9].toInt() and 0xFF)
    }

    @Test
    fun startButtonIsEncodedInHighButtonByteAndCanBeReleased() {
        val state = GamepadState()
        state.setButton(ButtonBit.START, true)

        var packet = state.toPacket()
        assertEquals(0x00, packet[2].toInt() and 0xFF)
        assertEquals(0x02, packet[3].toInt() and 0xFF)

        state.setButton(ButtonBit.START, false)
        packet = state.toPacket()
        assertEquals(0x00, packet[3].toInt() and 0xFF)
    }

    @Test
    fun setButtonSetsAndClearsOnlyRequestedBit() {
        val state = GamepadState()
        state.setButton(ButtonBit.A, true)
        state.setButton(ButtonBit.MODE, true)
        state.setButton(ButtonBit.A, false)
        assertEquals(ButtonBit.MODE, state.buttons)
    }
    @Test
    fun dpadCardinalAndDiagonalMasksMapToWireCodes() {
        assertEquals(0, DpadEncoding.fromCardinalMask(0))
        assertEquals(1, DpadEncoding.fromCardinalMask(1))
        assertEquals(2, DpadEncoding.fromCardinalMask(3))
        assertEquals(3, DpadEncoding.fromCardinalMask(2))
        assertEquals(4, DpadEncoding.fromCardinalMask(6))
        assertEquals(5, DpadEncoding.fromCardinalMask(4))
        assertEquals(6, DpadEncoding.fromCardinalMask(12))
        assertEquals(7, DpadEncoding.fromCardinalMask(8))
        assertEquals(8, DpadEncoding.fromCardinalMask(9))
    }

    @Test
    fun analogStickDeadZoneSuppressesSmallMovements() {
        val output = AnalogStickMapping.map(0.06f, 0.04f, 0.12f, 1f)
        assertEquals(0, output.x.toInt())
        assertEquals(0, output.y.toInt())
    }

    @Test
    fun analogStickReachesFullRangeOutsideDeadZone() {
        val output = AnalogStickMapping.map(1f, 0f, 0.12f, 1f)
        assertEquals(127, output.x.toInt())
        assertEquals(0, output.y.toInt())
    }

    @Test
    fun analogStickSensitivityScalesMidRangeMovement() {
        val normal = AnalogStickMapping.map(0.56f, 0f, 0.12f, 1f)
        val reduced = AnalogStickMapping.map(0.56f, 0f, 0.12f, 0.5f)
        assertEquals(normal.x.toInt() / 2, reduced.x.toInt())
    }

    @Test fun clearResetsAllGamepadControls() {
        val state = GamepadState().apply {
            buttons = 0x7ff
            leftX = 127; leftY = (-127).toByte()
            rightX = 64; rightY = (-64).toByte()
            leftTrigger = 255; rightTrigger = 200; dpad = 8
        }
        state.clear()
        assertEquals(0, state.buttons)
        assertEquals(0, state.leftX.toInt())
        assertEquals(0, state.leftY.toInt())
        assertEquals(0, state.rightX.toInt())
        assertEquals(0, state.rightY.toInt())
        assertEquals(0, state.leftTrigger)
        assertEquals(0, state.rightTrigger)
        assertEquals(0, state.dpad)
    }

}
