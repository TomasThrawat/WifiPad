package com.wifipad.receiver

/**
 * Mirrors controller-app's Protocol.kt exactly — 11-byte UDP packet.
 * See that file for the full field-by-field layout comment.
 */
object Protocol {
    const val MAGIC: Byte = 0x57
    const val VERSION: Byte = 1
    const val PACKET_SIZE = 11
    const val DEFAULT_PORT = 27191
}

object ButtonBit {
    const val A      = 1 shl 0
    const val B      = 1 shl 1
    const val X      = 1 shl 2
    const val Y      = 1 shl 3
    const val L1     = 1 shl 4
    const val R1     = 1 shl 5
    const val L3     = 1 shl 6
    const val R3     = 1 shl 7
    const val SELECT = 1 shl 8
    const val START  = 1 shl 9
    const val MODE   = 1 shl 10
}
