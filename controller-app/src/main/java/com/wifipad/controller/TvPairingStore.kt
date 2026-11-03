package com.wifipad.controller

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class TvPairing(
    val tvId: String,
    val controllerId: String,
    val secret: String,
    val host: String,
    val port: Int,
    val name: String
) {
    fun toJson(): JSONObject = JSONObject().put("tvId", tvId).put("controllerId", controllerId)
        .put("secret", secret).put("host", host).put("port", port).put("name", name)
}

internal object TvPairingStore {
    private const val KEY = "paired_tvs"

    fun all(context: Context): List<TvPairing> = runCatching {
        val data = JSONArray(EncryptedLocalStore.read(context, KEY) ?: "[]")
        (0 until data.length()).mapNotNull { index -> fromJson(data.optJSONObject(index)) }
    }.getOrDefault(emptyList())

    fun get(context: Context, tvId: String): TvPairing? = all(context).firstOrNull { it.tvId == tvId }

    fun save(context: Context, pairing: TvPairing) {
        val current = all(context).filterNot { it.tvId == pairing.tvId }.toMutableList()
        current += pairing
        val json = JSONArray().also { array -> current.forEach { array.put(it.toJson()) } }
        EncryptedLocalStore.write(context, KEY, json.toString())
    }

    fun updateHost(context: Context, tvId: String, host: String, port: Int) {
        get(context, tvId)?.let { save(context, it.copy(host = host, port = port)) }
    }

    fun remove(context: Context, tvId: String) {
        val json = JSONArray().also { array -> all(context).filterNot { it.tvId == tvId }.forEach { array.put(it.toJson()) } }
        EncryptedLocalStore.write(context, KEY, json.toString())
    }

    fun fromJson(json: JSONObject?): TvPairing? = runCatching {
        val source = requireNotNull(json)
        require(source.optString("app") == "WifiPad" || source.has("tvId"))
        val tvId = source.getString("tvId")
        val controllerId = source.getString("controllerId")
        val secret = source.getString("secret")
        require(SecureProtocol.fromHex(controllerId, 16) != null)
        require(SecureProtocol.fromHex(secret, 32) != null)
        val host = source.getString("host").trim()
        val port = source.optInt("port", 27191)
        require(host.isNotBlank() && port in 1..65535)
        TvPairing(tvId, controllerId, secret, host, port, source.optString("name", "WifiPad TV"))
    }.getOrNull()
}
