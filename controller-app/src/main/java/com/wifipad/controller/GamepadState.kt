package com.wifipad.controller

/** Current state of every control, written by the UI thread and read by the sender thread. */
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

    fun toPacket(): ByteArray {
        val b = ByteArray(Protocol.PACKET_SIZE)
        b[0] = Protocol.MAGIC
        b[1] = Protocol.VERSION
        b[2] = (buttons and 0xFF).toByte()
        b[3] = ((buttons shr 8) and 0xFF).toByte()
        b[4] = leftX
        b[5] = leftY
        b[6] = rightX
        b[7] = rightY
        b[8] = leftTrigger.toByte()
        b[9] = rightTrigger.toByte()
        b[10] = dpad.toByte()
        return b
    }
}
