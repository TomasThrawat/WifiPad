package com.wifipad.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerProfileTest {
    @Test
    fun retroAndPlayStationProfilesKeepStandardAndroidGamepadCodes() {
        assertEquals(listOf(ButtonBit.Y, ButtonBit.B, ButtonBit.A, ButtonBit.X),
            ControllerProfile.RETRO.faceButtons().map { it.bit })
        assertEquals(listOf("△", "○", "×", "□"),
            ControllerProfile.PLAYSTATION.faceButtons().map { it.label })
        assertEquals(listOf(ButtonBit.Y, ButtonBit.B, ButtonBit.A, ButtonBit.X),
            ControllerProfile.PLAYSTATION.faceButtons().map { it.bit })
    }

    @Test
    fun nintendoAndNesProfilesExposeExpectedFaceButtons() {
        assertEquals(listOf("X", "A", "B", "Y"),
            ControllerProfile.NINTENDO.faceButtons().map { it.label })
        assertEquals(listOf(ButtonBit.X, ButtonBit.A, ButtonBit.B, ButtonBit.Y),
            ControllerProfile.NINTENDO.faceButtons().map { it.bit })
        assertEquals(listOf("B", "A"), ControllerProfile.NES.faceButtons().map { it.label })
        assertFalse(ControllerProfile.NES.rightStickVisible)
        assertTrue(ControllerProfile.PSP.rightStickVisible)
    }

    @Test
    fun dpadEncodesAllCardinalAndDiagonalDirections() {
        assertEquals(1, DpadCode.fromPressedDirections(1))
        assertEquals(2, DpadCode.fromPressedDirections(1 or 2))
        assertEquals(3, DpadCode.fromPressedDirections(2))
        assertEquals(4, DpadCode.fromPressedDirections(2 or 4))
        assertEquals(5, DpadCode.fromPressedDirections(4))
        assertEquals(6, DpadCode.fromPressedDirections(4 or 8))
        assertEquals(7, DpadCode.fromPressedDirections(8))
        assertEquals(8, DpadCode.fromPressedDirections(8 or 1))
    }

    @Test
    fun opposingDpadDirectionsCancel() {
        assertEquals(0, DpadCode.fromPressedDirections(1 or 4))
        assertEquals(0, DpadCode.fromPressedDirections(2 or 8))
    }
}
