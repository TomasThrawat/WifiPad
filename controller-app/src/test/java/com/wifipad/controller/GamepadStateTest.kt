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
    fun writePacketReusesCallerBuffer() {
        val state = GamepadState().apply {
            buttons = ButtonBit.B
            leftX = 42
            rightTrigger = 128
        }
        val buffer = ByteArray(Protocol.PACKET_SIZE)
        state.writePacket(buffer)
        assertEquals(Protocol.MAGIC, buffer[0])
        assertEquals(Protocol.VERSION, buffer[1])
        assertEquals(0x02, buffer[2].toInt() and 0xFF)
        assertEquals(42, buffer[4].toInt())
        assertEquals(128, buffer[9].toInt() and 0xFF)
    }

    @Test
    fun dpadCodesCoverAllCardinalAndDiagonalDirections() {
        assertEquals(1, DpadCode.fromPressedDirections(1)) // up
        assertEquals(2, DpadCode.fromPressedDirections(1 or 2)) // up-right
        assertEquals(3, DpadCode.fromPressedDirections(2)) // right
        assertEquals(4, DpadCode.fromPressedDirections(2 or 4)) // down-right
        assertEquals(5, DpadCode.fromPressedDirections(4)) // down
        assertEquals(6, DpadCode.fromPressedDirections(4 or 8)) // down-left
        assertEquals(7, DpadCode.fromPressedDirections(8)) // left
        assertEquals(8, DpadCode.fromPressedDirections(8 or 1)) // up-left
        assertEquals(0, DpadCode.fromPressedDirections(0))
        assertEquals(0, DpadCode.fromPressedDirections(1 or 4)) // opposite directions cancel
    }

    @Test
    fun controllerProfilesMatchConsoleControlSets() {
        assertEquals(2, ControllerProfile.NES.faceButtons().size)
        assertEquals(false, ControllerProfile.NES.leftStickVisible)
        assertEquals(false, ControllerProfile.NES.rightStickVisible)
        assertEquals(false, ControllerProfile.NES.shouldersVisible)
        assertEquals(2, ControllerProfile.NES.auxiliaryButtons().size)
        assertEquals(false, ControllerProfile.PSP.rightStickVisible)
        assertEquals(true, ControllerProfile.PLAYSTATION.rightStickVisible)
        assertEquals(true, ControllerProfile.NINTENDO.rightStickVisible)
        assertEquals("○", ControllerProfile.PLAYSTATION.faceButtons()[1].label)
        assertEquals(ButtonBit.A, ControllerProfile.NINTENDO.faceButtons()[1].bit)
        assertEquals("Y", ControllerProfile.NINTENDO.faceButtons()[3].label)
        assertEquals(ButtonBit.SELECT, ControllerProfile.NES.auxiliaryButtons()[0].bit)
        assertEquals(ButtonBit.START, ControllerProfile.NES.auxiliaryButtons()[1].bit)
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
