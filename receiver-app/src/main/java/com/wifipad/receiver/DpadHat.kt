package com.wifipad.receiver

internal object DpadHat {
    fun fromWireValue(value: Int): Pair<Int, Int> = when (value) {
        1 -> 0 to -1
        2 -> 1 to -1
        3 -> 1 to 0
        4 -> 1 to 1
        5 -> 0 to 1
        6 -> -1 to 1
        7 -> -1 to 0
        8 -> -1 to -1
        else -> 0 to 0
    }
}
