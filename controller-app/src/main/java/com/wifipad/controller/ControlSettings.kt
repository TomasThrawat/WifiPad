package com.wifipad.controller

import android.content.Context

enum class ControlGroup(
    val key: String,
    val defaultVisible: Boolean,
    val defaultScale: Float,
    val defaultX: Float,
    val defaultY: Float
) {
    STICK("stick", true, 1.0f, 0.22f, 0.66f),
    RIGHT_STICK("right_stick", true, 1.0f, 0.78f, 0.66f),
    DPAD("dpad", true, 1.0f, 0.22f, 0.32f),
    FACE("face", true, 1.0f, 0.78f, 0.32f),
    LEFT_SHOULDER("left_shoulder", true, 1.0f, 0.12f, 0.14f),
    RIGHT_SHOULDER("right_shoulder", true, 1.0f, 0.88f, 0.14f)
}

data class FaceButtonLayout(val dx: Int, val dy: Int, val bit: Int, val label: String)
data class AuxiliaryButtonLayout(val dx: Int, val dy: Int, val bit: Int, val label: String)

enum class ControllerProfile(
    val key: String,
    val title: String,
    val leftStickVisible: Boolean,
    val rightStickVisible: Boolean,
    val shouldersVisible: Boolean
) {
    RETRO("retro", "Retro / Xbox", true, true, true),
    PLAYSTATION("playstation", "PlayStation (PS1–PS5)", true, true, true),
    PSP("psp", "PSP", true, false, true),
    NINTENDO("nintendo", "Nintendo / SNES / Switch", true, true, true),
    NES("nes", "NES / Famicom", false, false, false);

    fun faceButtons(): List<FaceButtonLayout> = when (this) {
        RETRO -> listOf(
            FaceButtonLayout(0, -1, ButtonBit.Y, "Y"),
            FaceButtonLayout(1, 0, ButtonBit.B, "B"),
            FaceButtonLayout(0, 1, ButtonBit.A, "A"),
            FaceButtonLayout(-1, 0, ButtonBit.X, "X")
        )
        PLAYSTATION, PSP -> listOf(
            FaceButtonLayout(0, -1, ButtonBit.Y, "△"),
            FaceButtonLayout(1, 0, ButtonBit.B, "○"),
            FaceButtonLayout(0, 1, ButtonBit.A, "×"),
            FaceButtonLayout(-1, 0, ButtonBit.X, "□")
        )
        NINTENDO -> listOf(
            FaceButtonLayout(0, -1, ButtonBit.X, "X"),
            FaceButtonLayout(1, 0, ButtonBit.A, "A"),
            FaceButtonLayout(0, 1, ButtonBit.B, "B"),
            FaceButtonLayout(-1, 0, ButtonBit.Y, "Y")
        )
        NES -> listOf(
            FaceButtonLayout(-1, 0, ButtonBit.B, "B"),
            FaceButtonLayout(1, 0, ButtonBit.A, "A")
        )
    }

    fun auxiliaryButtons(): List<AuxiliaryButtonLayout> = when (this) {
        RETRO -> listOf(
            AuxiliaryButtonLayout(-1, 0, ButtonBit.SELECT, "BACK"),
            AuxiliaryButtonLayout(1, 0, ButtonBit.START, "START"),
            AuxiliaryButtonLayout(0, 1, ButtonBit.MODE, "GUIDE")
        )
        PLAYSTATION -> listOf(
            AuxiliaryButtonLayout(-1, 0, ButtonBit.SELECT, "SHARE"),
            AuxiliaryButtonLayout(1, 0, ButtonBit.START, "OPTIONS"),
            AuxiliaryButtonLayout(0, 1, ButtonBit.MODE, "PS")
        )
        PSP -> listOf(
            AuxiliaryButtonLayout(-1, 0, ButtonBit.SELECT, "SELECT"),
            AuxiliaryButtonLayout(1, 0, ButtonBit.START, "START"),
            AuxiliaryButtonLayout(0, 1, ButtonBit.MODE, "HOME")
        )
        NINTENDO -> listOf(
            AuxiliaryButtonLayout(-1, 0, ButtonBit.SELECT, "−"),
            AuxiliaryButtonLayout(1, 0, ButtonBit.START, "+"),
            AuxiliaryButtonLayout(0, 1, ButtonBit.MODE, "HOME")
        )
        NES -> listOf(
            AuxiliaryButtonLayout(-1, 0, ButtonBit.SELECT, "SELECT"),
            AuxiliaryButtonLayout(1, 0, ButtonBit.START, "START")
        )
    }

    companion object {
        fun fromKey(key: String?): ControllerProfile =
            values().firstOrNull { it.key == key } ?: RETRO
    }
}

data class ControlSettings(
    val visible: Boolean,
    val scale: Float,
    val x: Float,
    val y: Float
)

object ControlSettingsStore {
    private const val PREFS = "wifipad_controls"
    private const val PROFILE_KEY = "controller_profile"

    fun loadProfile(context: Context): ControllerProfile =
        ControllerProfile.fromKey(prefs(context).getString(PROFILE_KEY, ControllerProfile.RETRO.key))

    fun saveProfile(context: Context, profile: ControllerProfile) {
        prefs(context).edit().putString(PROFILE_KEY, profile.key).apply()
    }
    private const val MIN_SCALE = 0.60f
    private const val MAX_SCALE = 1.60f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, group: ControlGroup): ControlSettings {
        val p = prefs(context)
        return ControlSettings(
            visible = p.getBoolean(group.key + "_visible", group.defaultVisible),
            scale = p.getFloat(group.key + "_scale", group.defaultScale)
                .coerceIn(MIN_SCALE, MAX_SCALE),
            x = p.getFloat(group.key + "_x", group.defaultX).coerceIn(0.05f, 0.95f),
            y = p.getFloat(group.key + "_y", group.defaultY).coerceIn(0.08f, 0.92f)
        )
    }

    fun save(context: Context, group: ControlGroup, settings: ControlSettings) {
        prefs(context).edit()
            .putBoolean(group.key + "_visible", settings.visible)
            .putFloat(group.key + "_scale", settings.scale.coerceIn(MIN_SCALE, MAX_SCALE))
            .putFloat(group.key + "_x", settings.x.coerceIn(0.05f, 0.95f))
            .putFloat(group.key + "_y", settings.y.coerceIn(0.08f, 0.92f))
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
