package com.wifipad.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ControllerLayoutTest {
    @Test
    fun universalFaceButtonsKeepXboxPositions() {
        val buttons = ControllerLayout.UNIVERSAL.faceButtons().associateBy { it.position }
        assertEquals("Y", buttons.getValue(FaceButtonPosition.TOP).label)
        assertEquals(ButtonBit.Y, buttons.getValue(FaceButtonPosition.TOP).bit)
        assertEquals("B", buttons.getValue(FaceButtonPosition.RIGHT).label)
        assertEquals(ButtonBit.B, buttons.getValue(FaceButtonPosition.RIGHT).bit)
        assertEquals("A", buttons.getValue(FaceButtonPosition.BOTTOM).label)
        assertEquals(ButtonBit.A, buttons.getValue(FaceButtonPosition.BOTTOM).bit)
        assertEquals("X", buttons.getValue(FaceButtonPosition.LEFT).label)
        assertEquals(ButtonBit.X, buttons.getValue(FaceButtonPosition.LEFT).bit)
    }

    @Test
    fun nintendoProfileKeepsLabelAndOutputBitTogether() {
        val buttons = ControllerLayout.NINTENDO.faceButtons().associateBy { it.position }
        assertEquals("X", buttons.getValue(FaceButtonPosition.TOP).label)
        assertEquals(ButtonBit.X, buttons.getValue(FaceButtonPosition.TOP).bit)
        assertEquals("A", buttons.getValue(FaceButtonPosition.RIGHT).label)
        assertEquals(ButtonBit.A, buttons.getValue(FaceButtonPosition.RIGHT).bit)
        assertEquals("B", buttons.getValue(FaceButtonPosition.BOTTOM).label)
        assertEquals(ButtonBit.B, buttons.getValue(FaceButtonPosition.BOTTOM).bit)
        assertEquals("Y", buttons.getValue(FaceButtonPosition.LEFT).label)
        assertEquals(ButtonBit.Y, buttons.getValue(FaceButtonPosition.LEFT).bit)
    }

    @Test
    fun playstationProfileUsesSymbolsWithStandardEquivalentBits() {
        val buttons = ControllerLayout.PLAYSTATION.faceButtons().associateBy { it.position }
        assertEquals("△", buttons.getValue(FaceButtonPosition.TOP).label)
        assertEquals(ButtonBit.Y, buttons.getValue(FaceButtonPosition.TOP).bit)
        assertEquals("○", buttons.getValue(FaceButtonPosition.RIGHT).label)
        assertEquals(ButtonBit.B, buttons.getValue(FaceButtonPosition.RIGHT).bit)
        assertEquals("×", buttons.getValue(FaceButtonPosition.BOTTOM).label)
        assertEquals(ButtonBit.A, buttons.getValue(FaceButtonPosition.BOTTOM).bit)
        assertEquals("□", buttons.getValue(FaceButtonPosition.LEFT).label)
        assertEquals(ButtonBit.X, buttons.getValue(FaceButtonPosition.LEFT).bit)
    }

    @Test
    fun analogStickHasDeadZoneAndPreservesAxisDirection() {
        val neutral = AnalogStick.project(5f, 0f, 100f)
        assertEquals(0, neutral.axisX.toInt())
        assertEquals(0, neutral.axisY.toInt())

        val right = AnalogStick.project(25f, 0f, 100f)
        assertTrue(right.axisX.toInt() > 0)
        assertEquals(0, right.axisY.toInt())

        val down = AnalogStick.project(0f, 100f, 100f)
        assertEquals(0, down.axisX.toInt())
        assertEquals(127, down.axisY.toInt())
    }

    @Test
    fun analogStickClampsMagnitudeAndAppliesSensitivity() {
        assertEquals(127, AnalogStick.project(500f, 0f, 100f).axisX.toInt())

        val low = AnalogStick.project(25f, 0f, 100f, StickSensitivity.LOW.multiplier)
        val normal = AnalogStick.project(25f, 0f, 100f, StickSensitivity.NORMAL.multiplier)
        val high = AnalogStick.project(25f, 0f, 100f, StickSensitivity.HIGH.multiplier)
        assertTrue(low.axisX.toInt() < normal.axisX.toInt())
        assertTrue(normal.axisX.toInt() < high.axisX.toInt())
    }

    @Test
    fun diagonalAnalogInputRemainsInsideTheCircularRange() {
        val vector = AnalogStick.project(100f, 100f, 100f)
        assertTrue(hypot(vector.offsetX.toDouble(), vector.offsetY.toDouble()) <= 100.001)
        assertTrue(vector.axisX.toInt() in 0..127)
        assertTrue(vector.axisY.toInt() in 0..127)
    }
}
