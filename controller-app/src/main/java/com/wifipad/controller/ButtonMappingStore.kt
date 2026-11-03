package com.wifipad.controller

import android.content.Context
import androidx.core.content.edit

internal object ButtonMappingStore {
    val positions = listOf("A", "B", "X", "Y", "L1", "R1", "L3", "R3", "SELECT", "START", "MODE")
    private val bits = mapOf(
        "A" to ButtonBit.A, "B" to ButtonBit.B, "X" to ButtonBit.X, "Y" to ButtonBit.Y,
        "L1" to ButtonBit.L1, "R1" to ButtonBit.R1,
        "L3" to ButtonBit.L3, "R3" to ButtonBit.R3,
        "SELECT" to ButtonBit.SELECT, "START" to ButtonBit.START, "MODE" to ButtonBit.MODE
    )
    private fun prefs(context: Context) = context.getSharedPreferences("wifipad_button_mappings", Context.MODE_PRIVATE)
    fun get(context: Context, position: String): String = prefs(context).getString(position, position) ?: position
    fun bit(context: Context, position: String): Int = bits[get(context, position)] ?: (bits[position] ?: 0)
    fun set(context: Context, position: String, target: String) {
        require(position in positions && target in bits.keys)
        prefs(context).edit { putString(position, target) }
    }
}
