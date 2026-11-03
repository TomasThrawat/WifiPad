package com.wifipad.controller

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authenticated WifiPad v2 wire frames. HMAC-SHA256 is truncated to 128 bits. */
internal object SecureProtocol {
    const val MAGIC: Byte = 0x57
    const val VERSION: Byte = 2
    const val HELLO: Byte = 1
    const val WELCOME: Byte = 2
    const val INPUT: Byte = 3
    const val ACK: Byte = 4
    const val PAYLOAD_SIZE = 9
    const val HELLO_SIZE = 43
    const val WELCOME_SIZE = 51
    const val INPUT_SIZE = 68
    const val ACK_SIZE = 59
    private const val MAC_SIZE = 16

    data class Ack(val sequence: Long)
    data class Input(
        val buttons: Int,
        val leftX: Int,
        val leftY: Int,
        val rightX: Int,
        val rightY: Int,
        val leftTrigger: Int,
        val rightTrigger: Int,
        val dpad: Int
    )

    fun encodeHello(controllerId: ByteArray, clientNonce: Long, secret: ByteArray): ByteArray {
        require(controllerId.size == 16 && secret.size == 32)
        val body = ByteBuffer.allocate(HELLO_SIZE - MAC_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).put(HELLO).put(controllerId).putLong(clientNonce).array()
        return body + mac(secret, body)
    }

    fun parseWelcome(frame: ByteArray, controllerId: ByteArray, clientNonce: Long, secret: ByteArray): Long? {
        if (frame.size != WELCOME_SIZE || controllerId.size != 16 || secret.size != 32) return null
        val body = frame.copyOfRange(0, frame.size - MAC_SIZE)
        if (!MessageDigest.isEqual(frame.copyOfRange(body.size, frame.size), mac(secret, body))) return null
        val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (b.get() != MAGIC || b.get() != VERSION || b.get() != WELCOME) return null
        val id = ByteArray(16).also(b::get)
        if (!MessageDigest.isEqual(id, controllerId) || b.long != clientNonce) return null
        return b.long
    }

    fun encodeInput(
        controllerId: ByteArray, clientNonce: Long, serverNonce: Long, sequence: Long,
        payload: ByteArray, secret: ByteArray
    ): ByteArray {
        require(controllerId.size == 16 && payload.size == PAYLOAD_SIZE && secret.size == 32)
        val body = ByteBuffer.allocate(INPUT_SIZE - MAC_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).put(INPUT).put(controllerId)
            .putLong(clientNonce).putLong(serverNonce).putLong(sequence).put(payload).array()
        return body + mac(secret, body)
    }

    fun parseAck(frame: ByteArray, controllerId: ByteArray, clientNonce: Long, serverNonce: Long, secret: ByteArray): Ack? {
        if (frame.size != ACK_SIZE || controllerId.size != 16 || secret.size != 32) return null
        val body = frame.copyOfRange(0, frame.size - MAC_SIZE)
        if (!MessageDigest.isEqual(frame.copyOfRange(body.size, frame.size), mac(secret, body))) return null
        val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (b.get() != MAGIC || b.get() != VERSION || b.get() != ACK) return null
        val id = ByteArray(16).also(b::get)
        if (!MessageDigest.isEqual(id, controllerId) || b.long != clientNonce || b.long != serverNonce) return null
        return Ack(b.long)
    }

    fun encodePayload(input: Input): ByteArray = ByteBuffer.allocate(PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
        .putShort(input.buttons.coerceIn(0, 0x7ff).toShort())
        .put(input.leftX.coerceIn(-127, 127).toByte())
        .put(input.leftY.coerceIn(-127, 127).toByte())
        .put(input.rightX.coerceIn(-127, 127).toByte())
        .put(input.rightY.coerceIn(-127, 127).toByte())
        .put(input.leftTrigger.coerceIn(0, 255).toByte())
        .put(input.rightTrigger.coerceIn(0, 255).toByte())
        .put(input.dpad.coerceIn(0, 8).toByte())
        .array()

    private fun mac(secret: ByteArray, body: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secret, "HmacSHA256"))
        doFinal(body).copyOf(MAC_SIZE)
    }

    fun fromHex(value: String, expectedBytes: Int): ByteArray? {
        if (value.length != expectedBytes * 2 || value.any { it.digitToIntOrNull(16) == null }) return null
        return runCatching { ByteArray(expectedBytes) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    fun toHex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }
}
