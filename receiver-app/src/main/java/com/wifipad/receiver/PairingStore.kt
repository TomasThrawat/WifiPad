package com.wifipad.receiver

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

internal data class ReceiverPairing(val tvId: String, val controllerId: String, val secret: String) {
    fun qrPayload(host: String, port: Int, name: String): String = JSONObject()
        .put("app", "WifiPad").put("version", 2).put("tvId", tvId)
        .put("controllerId", controllerId).put("secret", secret)
        .put("host", host).put("port", port).put("name", name).toString()
}

internal object PairingStore {
    private const val KEY = "receiver_pairing"
    fun current(context: Context): ReceiverPairing? = runCatching {
        val json = JSONObject(EncryptedLocalStore.read(context, KEY) ?: return null)
        val pairing = ReceiverPairing(json.getString("tvId"), json.getString("controllerId"), json.getString("secret"))
        require(SecureProtocol.fromHex(pairing.controllerId, 16) != null && SecureProtocol.fromHex(pairing.secret, 32) != null)
        pairing
    }.getOrNull()

    fun getOrCreate(context: Context): ReceiverPairing = current(context) ?: rotate(context)

    fun rotate(context: Context): ReceiverPairing {
        val id = ByteArray(16).also(SecureRandom()::nextBytes)
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        val pairing = ReceiverPairing(
            current(context)?.tvId ?: UUID.randomUUID().toString(),
            SecureProtocol.toHex(id), SecureProtocol.toHex(secret)
        )
        EncryptedLocalStore.write(context, KEY, JSONObject().put("tvId", pairing.tvId)
            .put("controllerId", pairing.controllerId).put("secret", pairing.secret).toString())
        return pairing
    }
}
