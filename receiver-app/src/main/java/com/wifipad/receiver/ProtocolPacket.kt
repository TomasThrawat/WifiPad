package com.wifipad.receiver

/** A validated v1 state packet. Legacy right-stick bytes 6-7 are ignored. */
internal data class ProtocolPacket(
    val buttons: Int,
    val leftX: Int,
    val leftY: Int,
    val leftTrigger: Int,
    val rightTrigger: Int,
    val dpad: Int
) {
    companion object {
        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size): ProtocolPacket? {
            if (length != Protocol.PACKET_SIZE || offset < 0 || offset > data.size - length) return null
            if (data[offset] != Protocol.MAGIC || data[offset + 1] != Protocol.VERSION) return null
            val buttons = (data[offset + 2].toInt() and 0xff) or
                ((data[offset + 3].toInt() and 0xff) shl 8)
            val leftX = data[offset + 4].toInt()
            val leftY = data[offset + 5].toInt()
            val dpad = data[offset + 10].toInt() and 0xff
            if (buttons !in 0..0x7ff || leftX !in -127..127 || leftY !in -127..127 || dpad !in 0..8) return null
            return ProtocolPacket(
                buttons, leftX, leftY,
                data[offset + 8].toInt() and 0xff,
                data[offset + 9].toInt() and 0xff,
                dpad
            )
        }
    }
}
