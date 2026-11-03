package com.wifipad.controller

import android.content.Context
import org.json.JSONObject

internal object GamepadProfileStore {
    data class Profile(val id: String, val label: String)
    val profiles = listOf(
        Profile("fps", "FPS"), Profile("racing", "Racing"),
        Profile("platformer", "Platformer"), Profile("custom", "Custom")
    )

    fun activate(context: Context, id: String) {
        require(profiles.any { it.id == id })
        ControlSettingsStore.activateProfile(context, id)
        val prefs = context.getSharedPreferences("wifipad_profiles", Context.MODE_PRIVATE)
        val initKey = "initialized_$id"
        if (prefs.getBoolean(initKey, false)) return
        val preset = when (id) {
            "fps" -> mapOf(
                ControlGroup.STICK to ControlSettings(true, 1.08f, 0.16f, 0.68f, 0.10f, 1.05f),
                ControlGroup.RIGHT_STICK to ControlSettings(true, 1.08f, 0.83f, 0.68f, 0.08f, 0.95f),
                ControlGroup.DPAD to ControlSettings(false, 0.9f, 0.20f, 0.30f),
                ControlGroup.FACE to ControlSettings(true, 0.92f, 0.86f, 0.31f),
                ControlGroup.SYSTEM to ControlSettings(true, 0.80f, 0.50f, 0.16f),
                ControlGroup.LEFT_SHOULDER to ControlSettings(true, 1.05f, 0.13f, 0.14f),
                ControlGroup.RIGHT_SHOULDER to ControlSettings(true, 1.05f, 0.87f, 0.14f)
            )
            "racing" -> mapOf(
                ControlGroup.STICK to ControlSettings(true, 1.25f, 0.16f, 0.70f, 0.06f, 1.0f),
                ControlGroup.RIGHT_STICK to ControlSettings(false, 1.0f, 0.82f, 0.68f),
                ControlGroup.DPAD to ControlSettings(false, 1.0f, 0.20f, 0.30f),
                ControlGroup.FACE to ControlSettings(true, 1.12f, 0.84f, 0.67f),
                ControlGroup.SYSTEM to ControlSettings(true, 0.72f, 0.50f, 0.15f),
                ControlGroup.LEFT_SHOULDER to ControlSettings(true, 1.22f, 0.13f, 0.15f),
                ControlGroup.RIGHT_SHOULDER to ControlSettings(true, 1.22f, 0.87f, 0.15f)
            )
            "platformer" -> mapOf(
                ControlGroup.STICK to ControlSettings(false, 1.0f, 0.18f, 0.67f),
                ControlGroup.RIGHT_STICK to ControlSettings(false, 1.0f, 0.82f, 0.67f),
                ControlGroup.DPAD to ControlSettings(true, 1.20f, 0.19f, 0.68f),
                ControlGroup.FACE to ControlSettings(true, 1.18f, 0.84f, 0.66f),
                ControlGroup.SYSTEM to ControlSettings(true, 0.72f, 0.50f, 0.16f),
                ControlGroup.LEFT_SHOULDER to ControlSettings(true, 1.0f, 0.12f, 0.14f),
                ControlGroup.RIGHT_SHOULDER to ControlSettings(true, 1.0f, 0.88f, 0.14f)
            )
            else -> return
        }
        preset.forEach { (group, settings) -> ControlSettingsStore.save(context, group, settings) }
        prefs.edit().putBoolean(initKey, true).apply()
    }

    fun export(context: Context): String {
        val profile = ControlSettingsStore.activeProfile(context)
        val controls = JSONObject()
        ControlGroup.values().forEach { group ->
            val item = ControlSettingsStore.load(context, group)
            controls.put(group.key, JSONObject().put("visible", item.visible).put("scale", item.scale)
                .put("x", item.x).put("y", item.y).put("deadZone", item.deadZone).put("sensitivity", item.sensitivity))
        }
        val mappings = JSONObject()
        ButtonMappingStore.positions.forEach { label -> mappings.put(label, ButtonMappingStore.get(context, label)) }
        return JSONObject().put("app", "WifiPad").put("schema", 1).put("profile", profile)
            .put("controls", controls).put("buttonMappings", mappings).toString(2)
    }

    fun import(context: Context, raw: String): Boolean = runCatching {
        val json = JSONObject(raw)
        require(json.getString("app") == "WifiPad" && json.getInt("schema") == 1)
        val profile = json.optString("profile", "custom")
        require(profiles.any { it.id == profile })
        ControlSettingsStore.activateProfile(context, profile)
        val controls = json.getJSONObject("controls")
        ControlGroup.values().forEach { group ->
            val item = controls.getJSONObject(group.key)
            ControlSettingsStore.save(context, group, ControlSettings(
                item.getBoolean("visible"), item.getDouble("scale").toFloat(),
                item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                item.optDouble("deadZone", 0.12).toFloat(), item.optDouble("sensitivity", 1.0).toFloat()
            ))
        }
        val maps = json.optJSONObject("buttonMappings")
        if (maps != null) ButtonMappingStore.positions.forEach { label ->
            if (maps.has(label)) ButtonMappingStore.set(context, label, maps.getString(label))
        }
        true
    }.getOrDefault(false)
}
