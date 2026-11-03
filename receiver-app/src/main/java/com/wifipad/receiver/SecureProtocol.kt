package com.wifipad.receiver

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authenticated WifiPad v2 frames. The receiver never accepts unauthenticated v1 input. */
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

    data class Hello(val controllerId: ByteArray, val clientNonce: Long)
    data class InputFrame(val controllerId: ByteArray, val clientNonce: Long, val serverNonce: Long, val sequence: Long, val input: Input)
    data class Input(
        val buttons: Int, val leftX: Int, val leftY: Int, val rightX: Int, val rightY: Int,
        val leftTrigger: Int, val rightTrigger: Int, val dpad: Int
    )

    fun parseHello(frame: ByteArray, expectedId: ByteArray, secret: ByteArray): Hello? {
        if (frame.size != HELLO_SIZE || expectedId.size != 16 || secret.size != 32) return null
        val body = frame.copyOfRange(0, frame.size - MAC_SIZE)
        if (!MessageDigest.isEqual(frame.copyOfRange(body.size, frame.size), mac(secret, body))) return null
        val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (b.get() != MAGIC || b.get() != VERSION || b.get() != HELLO) return null
        val id = ByteArray(16).also(b::get)
        if (!MessageDigest.isEqual(id, expectedId)) return null
        return Hello(id, b.long)
    }

    fun encodeWelcome(controllerId: ByteArray, clientNonce: Long, serverNonce: Long, secret: ByteArray): ByteArray {
        val body = ByteBuffer.allocate(WELCOME_SIZE - MAC_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).put(WELCOME).put(controllerId).putLong(clientNonce).putLong(serverNonce).array()
        return body + mac(secret, body)
    }

    fun parseInput(frame: ByteArray, expectedId: ByteArray, clientNonce: Long, serverNonce: Long, secret: ByteArray): InputFrame? {
        if (frame.size != INPUT_SIZE || expectedId.size != 16 || secret.size != 32) return null
        val body = frame.copyOfRange(0, frame.size - MAC_SIZE)
        if (!MessageDigest.isEqual(frame.copyOfRange(body.size, frame.size), mac(secret, body))) return null
        val b = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
        if (b.get() != MAGIC || b.get() != VERSION || b.get() != INPUT) return null
        val id = ByteArray(16).also(b::get)
        if (!MessageDigest.isEqual(id, expectedId)) return null
        val client = b.long
        val server = b.long
        if (client != clientNonce || server != serverNonce) return null
        val sequence = b.long
        val input = Input(
            b.short.toInt() and 0xffff,
            b.get().toInt(), b.get().toInt(), b.get().toInt(), b.get().toInt(),
            b.get().toInt() and 0xff, b.get().toInt() and 0xff, b.get().toInt() and 0xff
        )
        if (input.buttons !in 0..0x7ff ||
            input.leftX !in -127..127 || input.leftY !in -127..127 ||
            input.rightX !in -127..127 || input.rightY !in -127..127 || input.dpad !in 0..8
        ) return null
        return InputFrame(id, client, server, sequence, input)
    }

    fun encodeAck(controllerId: ByteArray, clientNonce: Long, serverNonce: Long, sequence: Long, secret: ByteArray): ByteArray {
        val body = ByteBuffer.allocate(ACK_SIZE - MAC_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC).put(VERSION).put(ACK).put(controllerId).putLong(clientNonce).putLong(serverNonce).putLong(sequence).array()
        return body + mac(secret, body)
    }

    private fun mac(secret: ByteArray, body: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secret, "HmacSHA256")); doFinal(body).copyOf(MAC_SIZE)
    }

    fun fromHex(value: String, expectedBytes: Int): ByteArray? {
        if (value.length != expectedBytes * 2 || value.any { it.digitToIntOrNull(16) == null }) return null
        return runCatching { ByteArray(expectedBytes) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
    fun toHex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }
}
