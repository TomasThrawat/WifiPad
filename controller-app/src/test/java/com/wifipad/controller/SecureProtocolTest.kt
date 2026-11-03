package com.wifipad.controller

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecureProtocolTest {
    private val id = ByteArray(16) { it.toByte() }
    private val key = ByteArray(32) { (it * 3).toByte() }

    private fun signedWelcome(clientNonce: Long, serverNonce: Long): ByteArray {
        val body = ByteBuffer.allocate(SecureProtocol.WELCOME_SIZE - 16).order(ByteOrder.BIG_ENDIAN)
            .put(SecureProtocol.MAGIC).put(SecureProtocol.VERSION).put(SecureProtocol.WELCOME)
            .put(id).putLong(clientNonce).putLong(serverNonce).array()
        return body + mac(body)
    }

    private fun signedAck(clientNonce: Long, serverNonce: Long, sequence: Long): ByteArray {
        val body = ByteBuffer.allocate(SecureProtocol.ACK_SIZE - 16).order(ByteOrder.BIG_ENDIAN)
            .put(SecureProtocol.MAGIC).put(SecureProtocol.VERSION).put(SecureProtocol.ACK)
            .put(id).putLong(clientNonce).putLong(serverNonce).putLong(sequence).array()
        return body + mac(body)
    }

    private fun mac(body: ByteArray) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(body).copyOf(16)
    }

    @Test fun helloCarriesExpectedIdentityAndFreshNonce() {
        val frame = SecureProtocol.encodeHello(id, 123L, key)
        assertEquals(SecureProtocol.HELLO_SIZE, frame.size)
        assertEquals(SecureProtocol.HELLO, frame[2])
    }

    @Test fun welcomeRequiresMatchingMacIdentityAndClientNonce() {
        assertEquals(456L, SecureProtocol.parseWelcome(signedWelcome(123L, 456L), id, 123L, key))
        assertNull(SecureProtocol.parseWelcome(signedWelcome(123L, 456L), id, 124L, key))
        val wrongId = id.copyOf().also { it[0] = 99 }
        assertNull(SecureProtocol.parseWelcome(signedWelcome(123L, 456L), wrongId, 123L, key))
    }

    @Test fun inputFrameAndAckUseExpectedAuthenticatedSizes() {
        val payload = SecureProtocol.encodePayload(SecureProtocol.Input(
            buttons = ButtonBit.R3, leftX = -127, leftY = 127, rightX = 10, rightY = -10,
            leftTrigger = 0, rightTrigger = 255, dpad = 8
        ))
        assertEquals(SecureProtocol.PAYLOAD_SIZE, payload.size)
        val frame = SecureProtocol.encodeInput(id, 1L, 2L, 3L, payload, key)
        assertEquals(68, frame.size)
        assertEquals(59, signedAck(1L, 2L, 3L).size)
    }

    @Test fun tamperedMacAndOldProtocolAreNotAcceptedAsSecureFrames() {
        val frame = signedWelcome(1L, 2L)
        frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()
        assertNull(SecureProtocol.parseWelcome(frame, id, 1L, key))
        assertNotEquals(1, SecureProtocol.VERSION.toInt())
    }
}
