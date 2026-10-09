package com.wifipad.controller

import android.content.Context

enum class FaceButtonPosition {
    TOP, RIGHT, BOTTOM, LEFT
}

data class FaceButtonSpec(
    val position: FaceButtonPosition,
    val bit: Int,
    val label: String
)

enum class ControllerLayout(val key: String, val titleResId: Int) {
    UNIVERSAL("universal", R.string.layout_universal) {
        override fun faceButtons() = listOf(
            FaceButtonSpec(FaceButtonPosition.TOP, ButtonBit.Y, "Y"),
            FaceButtonSpec(FaceButtonPosition.RIGHT, ButtonBit.B, "B"),
            FaceButtonSpec(FaceButtonPosition.BOTTOM, ButtonBit.A, "A"),
            FaceButtonSpec(FaceButtonPosition.LEFT, ButtonBit.X, "X")
        )
    },
    NINTENDO("nintendo", R.string.layout_nintendo) {
        override fun faceButtons() = listOf(
            FaceButtonSpec(FaceButtonPosition.TOP, ButtonBit.X, "X"),
            FaceButtonSpec(FaceButtonPosition.RIGHT, ButtonBit.A, "A"),
            FaceButtonSpec(FaceButtonPosition.BOTTOM, ButtonBit.B, "B"),
            FaceButtonSpec(FaceButtonPosition.LEFT, ButtonBit.Y, "Y")
        )
    },
    PLAYSTATION("playstation", R.string.layout_playstation) {
        override fun faceButtons() = listOf(
            FaceButtonSpec(FaceButtonPosition.TOP, ButtonBit.Y, "△"),
            FaceButtonSpec(FaceButtonPosition.RIGHT, ButtonBit.B, "○"),
            FaceButtonSpec(FaceButtonPosition.BOTTOM, ButtonBit.A, "×"),
            FaceButtonSpec(FaceButtonPosition.LEFT, ButtonBit.X, "□")
        )
    };

    abstract fun faceButtons(): List<FaceButtonSpec>
}

enum class StickSensitivity(
    val key: String,
    val titleResId: Int,
    val multiplier: Float
) {
    LOW("low", R.string.sensitivity_low, 0.75f),
    NORMAL("normal", R.string.sensitivity_normal, 1.0f),
    HIGH("high", R.string.sensitivity_high, 1.35f)
}

object ControllerLayoutStore {
    private const val PREFS = "wifipad_controller_preferences"
    private const val LAYOUT_KEY = "controller_layout"
    private const val SENSITIVITY_KEY = "stick_sensitivity"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ControllerLayout {
        val key = preferences(context).getString(LAYOUT_KEY, ControllerLayout.UNIVERSAL.key)
        return ControllerLayout.values().firstOrNull { it.key == key }
            ?: ControllerLayout.UNIVERSAL
    }

    fun save(context: Context, layout: ControllerLayout) {
        preferences(context).edit().putString(LAYOUT_KEY, layout.key).apply()
    }

    fun loadSensitivity(context: Context): StickSensitivity {
        val key = preferences(context).getString(SENSITIVITY_KEY, StickSensitivity.NORMAL.key)
        return StickSensitivity.values().firstOrNull { it.key == key }
            ?: StickSensitivity.NORMAL
    }

    fun saveSensitivity(context: Context, sensitivity: StickSensitivity) {
        preferences(context).edit().putString(SENSITIVITY_KEY, sensitivity.key).apply()
    }
}
