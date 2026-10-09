package com.wifipad.controller

object Protocol {
    const val MAGIC: Byte = 0x57
    const val VERSION: Byte = 1
    const val PACKET_SIZE = 11
    const val DEFAULT_PORT = 27191
    const val FAILSAFE_TIMEOUT_MS = 750L
}

object ButtonBit {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val L1 = 1 shl 4
    const val R1 = 1 shl 5
    const val L3 = 1 shl 6
    const val R3 = 1 shl 7
    const val SELECT = 1 shl 8
    const val START = 1 shl 9
    const val MODE = 1 shl 10
}


/**
 * Encodes all simultaneously-held D-pad directions as the receiver's 0-8
 * hat-switch values. Direction flags are up=1, right=2, down=4, left=8.
 */
object DpadCode {
    fun fromPressedDirections(mask: Int): Int {
        val directions = mask and 0x0F
        val horizontal = (if (directions and 2 != 0) 1 else 0) -
            (if (directions and 8 != 0) 1 else 0)
        val vertical = (if (directions and 4 != 0) 1 else 0) -
            (if (directions and 1 != 0) 1 else 0)
        return when {
            horizontal == 0 && vertical == -1 -> 1
            horizontal == 1 && vertical == -1 -> 2
            horizontal == 1 && vertical == 0 -> 3
            horizontal == 1 && vertical == 1 -> 4
            horizontal == 0 && vertical == 1 -> 5
            horizontal == -1 && vertical == 1 -> 6
            horizontal == -1 && vertical == 0 -> 7
            horizontal == -1 && vertical == -1 -> 8
            else -> 0
        }
    }
}
