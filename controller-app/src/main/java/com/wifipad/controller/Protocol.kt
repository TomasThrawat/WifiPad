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
 * Converts simultaneous D-pad direction flags into the wire protocol's 0-8
 * direction code. Direction flags are up=1, right=2, down=4, left=8.
 */
object DpadCode {
    fun fromPressedDirections(mask: Int): Int {
        val directions = mask and 0x0F
        val horizontal = (if (directions and 2 != 0) 1 else 0) -
            (if (directions and 8 != 0) 1 else 0)
        val vertical = (if (directions and 4 != 0) 1 else 0) -
            (if (directions and 1 != 0) 1 else 0)
        return when {
            horizontal == 0 && vertical == -1 -> 1 // up
            horizontal == 1 && vertical == -1 -> 2 // up-right
            horizontal == 1 && vertical == 0 -> 3 // right
            horizontal == 1 && vertical == 1 -> 4 // down-right
            horizontal == 0 && vertical == 1 -> 5 // down
            horizontal == -1 && vertical == 1 -> 6 // down-left
            horizontal == -1 && vertical == 0 -> 7 // left
            horizontal == -1 && vertical == -1 -> 8 // up-left
            else -> 0
        }
    }
}
