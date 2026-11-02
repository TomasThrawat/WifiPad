package com.wifipad.controller

import android.content.Context

enum class ControlGroup(
    val key: String,
    val defaultVisible: Boolean,
    val defaultScale: Float,
    val defaultX: Float,
    val defaultY: Float
) {
    STICK("stick", true, 1.0f, 0.18f, 0.66f),
    RIGHT_STICK("right_stick", false, 1.0f, 0.60f, 0.66f),
    DPAD("dpad", true, 1.0f, 0.20f, 0.31f),
    FACE("face", true, 1.0f, 0.84f, 0.64f),
    LEFT_SHOULDER("left_shoulder", true, 1.0f, 0.12f, 0.14f),
    RIGHT_SHOULDER("right_shoulder", true, 1.0f, 0.88f, 0.14f)
}

data class ControlSettings(
    val visible: Boolean,
    val scale: Float,
    val x: Float,
    val y: Float,
    val deadZone: Float = 0.12f,
    val sensitivity: Float = 1.0f
)

object ControlSettingsStore {
    private const val PREFS = "wifipad_controls"
    private const val MIN_SCALE = 0.60f
    private const val MAX_SCALE = 1.60f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, group: ControlGroup): ControlSettings {
        val p = prefs(context)
        return ControlSettings(
            visible = group != ControlGroup.RIGHT_STICK &&
                p.getBoolean(group.key + "_visible", group.defaultVisible),
            scale = p.getFloat(group.key + "_scale", group.defaultScale)
                .coerceIn(MIN_SCALE, MAX_SCALE),
            x = p.getFloat(group.key + "_x", group.defaultX).coerceIn(0.05f, 0.95f),
            y = p.getFloat(group.key + "_y", group.defaultY).coerceIn(0.08f, 0.92f),
            deadZone = p.getFloat(group.key + "_dead_zone", 0.12f).coerceIn(0f, 0.35f),
            sensitivity = p.getFloat(group.key + "_sensitivity", 1.0f).coerceIn(0.5f, 1.5f)
        )
    }

    fun save(context: Context, group: ControlGroup, settings: ControlSettings) {
        prefs(context).edit()
            .putBoolean(group.key + "_visible", group != ControlGroup.RIGHT_STICK && settings.visible)
            .putFloat(group.key + "_scale", settings.scale.coerceIn(MIN_SCALE, MAX_SCALE))
            .putFloat(group.key + "_x", settings.x.coerceIn(0.05f, 0.95f))
            .putFloat(group.key + "_y", settings.y.coerceIn(0.08f, 0.92f))
            .putFloat(group.key + "_dead_zone", settings.deadZone.coerceIn(0f, 0.35f))
            .putFloat(group.key + "_sensitivity", settings.sensitivity.coerceIn(0.5f, 1.5f))
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
