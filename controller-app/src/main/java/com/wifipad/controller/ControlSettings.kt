package com.wifipad.controller

import android.content.Context
import androidx.core.content.edit

enum class ControlGroup(
    val key: String,
    val defaultVisible: Boolean,
    val defaultScale: Float,
    val defaultX: Float,
    val defaultY: Float
) {
    STICK("stick", true, 1.0f, 0.18f, 0.66f),
    RIGHT_STICK("right_stick", true, 1.0f, 0.56f, 0.66f),
    DPAD("dpad", true, 1.0f, 0.20f, 0.31f),
    FACE("face", true, 1.0f, 0.84f, 0.64f),
    SYSTEM("system", true, 1.0f, 0.50f, 0.20f),
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
    private const val PROFILE_PREFS = "wifipad_profiles"
    private const val ACTIVE_PROFILE = "active_profile"

    fun activeProfile(context: Context): String = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_PROFILE, "custom") ?: "custom"

    fun activateProfile(context: Context, id: String) {
        require(id in setOf("fps", "racing", "platformer", "custom"))
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit { putString(ACTIVE_PROFILE, id) }
    }
    private const val MIN_SCALE = 0.60f
    private const val MAX_SCALE = 1.60f
    private const val RIGHT_STICK_VISIBILITY_MIGRATED = "right_stick_visibility_migrated"

    private fun prefs(context: Context) = context.getSharedPreferences(
        if (activeProfile(context) == "custom") PREFS else "${PREFS}_${activeProfile(context)}",
        Context.MODE_PRIVATE
    )

    fun load(context: Context, group: ControlGroup): ControlSettings {
        val p = prefs(context)
        if (group == ControlGroup.RIGHT_STICK && !p.getBoolean(RIGHT_STICK_VISIBILITY_MIGRATED, false)) {
            p.edit {
                putBoolean(group.key + "_visible", true)
                putBoolean(RIGHT_STICK_VISIBILITY_MIGRATED, true)
            }
        }
        return ControlSettings(
            visible = p.getBoolean(group.key + "_visible", group.defaultVisible),
            scale = p.getFloat(group.key + "_scale", group.defaultScale)
                .coerceIn(MIN_SCALE, MAX_SCALE),
            x = p.getFloat(group.key + "_x", group.defaultX).coerceIn(0.05f, 0.95f),
            y = p.getFloat(group.key + "_y", group.defaultY).coerceIn(0.08f, 0.92f),
            deadZone = p.getFloat(group.key + "_dead_zone", 0.12f).coerceIn(0f, 0.35f),
            sensitivity = p.getFloat(group.key + "_sensitivity", 1.0f).coerceIn(0.5f, 1.5f)
        )
    }

    fun save(context: Context, group: ControlGroup, settings: ControlSettings) {
        prefs(context).edit {
            putBoolean(group.key + "_visible", settings.visible)
            putFloat(group.key + "_scale", settings.scale.coerceIn(MIN_SCALE, MAX_SCALE))
            putFloat(group.key + "_x", settings.x.coerceIn(0.05f, 0.95f))
            putFloat(group.key + "_y", settings.y.coerceIn(0.08f, 0.92f))
            putFloat(group.key + "_dead_zone", settings.deadZone.coerceIn(0f, 0.35f))
            putFloat(group.key + "_sensitivity", settings.sensitivity.coerceIn(0.5f, 1.5f))
        }
    }

    fun reset(context: Context) {
        prefs(context).edit { clear() }
    }
}
