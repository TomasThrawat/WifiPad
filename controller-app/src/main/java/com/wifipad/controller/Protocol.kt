package com.wifipad.controller

/**
 * WifiPad UDP packet layout — 11 bytes, sent at a fixed rate over WiFi.
 *
 *  offset  field          type    range
 *  0       magic          byte    always 0x57 ('W')
 *  1       version        byte    protocol version, currently 1
 *  2..3    buttons        uint16  bitmask, see ButtonBit (byte 2 = low byte)
 *  4       leftX          int8    -127..127
 *  5       leftY          int8    -127..127
 *  6       rightX         int8    -127..127
 *  7       rightY         int8    -127..127
 *  8       leftTrigger    uint8   0..255 (L2)
 *  9       rightTrigger   uint8   0..255 (R2)
 *  10      dpad           uint8   0=none 1=up 2=up-right 3=right 4=down-right
 *                                 5=down 6=down-left 7=left 8=up-left
 *
 * This exact layout is mirrored in the receiver app's Protocol.kt — keep both
 * in sync if you change it.
 */
object Protocol {
    const val MAGIC: Byte = 0x57
    const val VERSION: Byte = 1
    const val PACKET_SIZE = 11
    const val DEFAULT_PORT = 27191
}

object ButtonBit {
    const val A      = 1 shl 0  // Cross
    const val B      = 1 shl 1  // Circle
    const val X      = 1 shl 2  // Square
    const val Y      = 1 shl 3  // Triangle
    const val L1     = 1 shl 4
    const val R1     = 1 shl 5
    const val L3     = 1 shl 6  // left stick click
    const val R3     = 1 shl 7  // right stick click
    const val SELECT = 1 shl 8  // Share
    const val START  = 1 shl 9  // Options
    const val MODE   = 1 shl 10 // PS / guide button
}
