package com.wifipad.controller

import kotlin.math.roundToInt

class GamepadState {
    @Volatile var buttons: Int = 0
    @Volatile var leftX: Byte = 0
    @Volatile var leftY: Byte = 0
    @Volatile var rightX: Byte = 0
    @Volatile var rightY: Byte = 0
    @Volatile var leftTrigger: Int = 0
    @Volatile var rightTrigger: Int = 0
    @Volatile var dpad: Int = 0

    fun setButton(bit: Int, pressed: Boolean) {
        buttons = if (pressed) buttons or bit else buttons and bit.inv()
    }

    fun writePacket(buffer: ByteArray) {
        require(buffer.size >= Protocol.PACKET_SIZE)
        val currentButtons = buttons
        buffer[0] = Protocol.MAGIC
        buffer[1] = Protocol.VERSION
        buffer[2] = (currentButtons and 0xFF).toByte()
        buffer[3] = ((currentButtons ushr 8) and 0xFF).toByte()
        buffer[4] = leftX
        buffer[5] = leftY
        buffer[6] = rightX
        buffer[7] = rightY
        buffer[8] = leftTrigger.coerceIn(0, 255).toByte()
        buffer[9] = rightTrigger.coerceIn(0, 255).toByte()
        buffer[10] = dpad.coerceIn(0, 255).toByte()
    }

    fun toPacket(): ByteArray = ByteArray(Protocol.PACKET_SIZE).also(::writePacket)
}


internal object DpadEncoding {
    // Source mask: UP=1, RIGHT=2, DOWN=4, LEFT=8. Wire values are N, NE, E, SE, S, SW, W, NW.
    fun fromCardinalMask(mask: Int): Int = when (mask and 0x0F) {
        0x1 -> 1
        0x3 -> 2
        0x2 -> 3
        0x6 -> 4
        0x4 -> 5
        0xC -> 6
        0x8 -> 7
        0x9 -> 8
        else -> 0
    }
}


internal data class AnalogStickOutput(val x: Byte, val y: Byte)

internal object AnalogStickMapping {
    fun map(normalizedX: Float, normalizedY: Float, deadZone: Float, sensitivity: Float): AnalogStickOutput {
        val x = normalizedX.coerceIn(-1f, 1f)
        val y = normalizedY.coerceIn(-1f, 1f)
        val magnitude = kotlin.math.hypot(x.toDouble(), y.toDouble()).toFloat().coerceIn(0f, 1f)
        val zone = deadZone.coerceIn(0f, 0.35f)
        val gain = sensitivity.coerceIn(0.5f, 1.5f)
        val outputMagnitude = if (magnitude <= zone) 0f else
            ((magnitude - zone) / (1f - zone) * gain).coerceIn(0f, 1f)
        val scale = if (magnitude <= 0f) 0f else outputMagnitude / magnitude
        return AnalogStickOutput(
            (x * scale * 127f).roundToInt().coerceIn(-127, 127).toByte(),
            (y * scale * 127f).roundToInt().coerceIn(-127, 127).toByte()
        )
    }
}
