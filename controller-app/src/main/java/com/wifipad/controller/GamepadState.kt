package com.wifipad.controller

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
