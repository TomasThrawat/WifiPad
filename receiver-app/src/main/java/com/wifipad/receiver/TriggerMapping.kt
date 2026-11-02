package com.wifipad.receiver

/** Linux input codes used by Android's Generic.kl for standard gamepad triggers. */
internal object TriggerMapping {
    const val ABS_GAS = 9
    const val ABS_BRAKE = 10
    const val BTN_TL2 = 312
    const val BTN_TR2 = 313
    const val DIGITAL_THRESHOLD = 128

    fun isPressed(value: Int): Boolean = value >= DIGITAL_THRESHOLD
}
