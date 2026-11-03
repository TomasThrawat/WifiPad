package com.wifipad.receiver

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SecureProtocolTest {
    private val id = ByteArray(16) { (it + 1).toByte() }
    private val key = ByteArray(32) { (it * 7).toByte() }

    private fun mac(body: ByteArray) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(body).copyOf(16)
    }

    private fun hello(nonce: Long): ByteArray {
        val body = ByteBuffer.allocate(SecureProtocol.HELLO_SIZE - 16).order(ByteOrder.BIG_ENDIAN)
            .put(SecureProtocol.MAGIC).put(SecureProtocol.VERSION).put(SecureProtocol.HELLO)
            .put(id).putLong(nonce).array()
        return body + mac(body)
    }

    private fun input(client: Long, server: Long, sequence: Long, corruptMac: Boolean = false): ByteArray {
        val payload = ByteBuffer.allocate(SecureProtocol.PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
            .putShort((ButtonBit.A or ButtonBit.R3).toShort())
            .put((-127).toByte()).put(127.toByte()).put(10.toByte()).put((-10).toByte())
            .put(0).put(255.toByte()).put(8.toByte()).array()
        val body = ByteBuffer.allocate(SecureProtocol.INPUT_SIZE - 16).order(ByteOrder.BIG_ENDIAN)
            .put(SecureProtocol.MAGIC).put(SecureProtocol.VERSION).put(SecureProtocol.INPUT)
            .put(id).putLong(client).putLong(server).putLong(sequence).put(payload).array()
        val frame = body + mac(body)
        if (corruptMac) frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()
        return frame
    }

    @Test fun acceptsOnlyPairedControllerHello() {
        val parsed = SecureProtocol.parseHello(hello(42L), id, key)
        assertNotNull(parsed)
        assertEquals(42L, parsed!!.clientNonce)
        val wrongId = id.copyOf().also { it[1] = 0 }
        assertNull(SecureProtocol.parseHello(hello(42L), wrongId, key))
    }

    @Test fun welcomeContainsFreshServerNonceAndAuthentication() {
        val frame = SecureProtocol.encodeWelcome(id, 42L, 99L, key)
        assertEquals(SecureProtocol.WELCOME_SIZE, frame.size)
        assertArrayEquals(mac(frame.copyOfRange(0, frame.size - 16)), frame.copyOfRange(frame.size - 16, frame.size))
    }

    @Test fun parsesAuthenticatedInputAndKeepsBothStickAxesSeparate() {
        val parsed = SecureProtocol.parseInput(input(42L, 99L, 7L), id, 42L, 99L, key)
        assertNotNull(parsed)
        assertEquals(7L, parsed!!.sequence)
        assertEquals(-127, parsed.input.leftX)
        assertEquals(127, parsed.input.leftY)
        assertEquals(10, parsed.input.rightX)
        assertEquals(-10, parsed.input.rightY)
        assertEquals(255, parsed.input.rightTrigger)
    }

    @Test fun rejectsWrongSessionReplayTamperingAndWrongLength() {
        val valid = input(42L, 99L, 7L)
        assertNull(SecureProtocol.parseInput(valid, id, 42L, 100L, key))
        assertNull(SecureProtocol.parseInput(input(42L, 99L, 7L, true), id, 42L, 99L, key))
        assertNull(SecureProtocol.parseInput(valid.copyOf(valid.size - 1), id, 42L, 99L, key))
        assertNull(SecureProtocol.parseInput(hello(42L), id, 42L, 99L, key))
    }

    @Test fun rejectsOutOfRangeAxesDpadAndReservedButtonsEvenWithValidMac() {
        fun resign(frame: ByteArray, offset: Int, value: Byte) {
            frame[offset] = value
            val body = frame.copyOfRange(0, frame.size - 16)
            val tag = mac(body)
            tag.copyInto(frame, frame.size - 16)
        }
        val badAxis = input(42L, 99L, 8L)
        resign(badAxis, 45, 0x80.toByte())
        assertNull(SecureProtocol.parseInput(badAxis, id, 42L, 99L, key))
        val badDpad = input(42L, 99L, 8L)
        resign(badDpad, 51, 9)
        assertNull(SecureProtocol.parseInput(badDpad, id, 42L, 99L, key))
        val badButtons = input(42L, 99L, 8L)
        resign(badButtons, 43, 0x08.toByte())
        assertNull(SecureProtocol.parseInput(badButtons, id, 42L, 99L, key))
    }

    @Test fun ackIsAuthenticatedAndCarriesSequence() {
        val ack = SecureProtocol.encodeAck(id, 42L, 99L, 7L, key)
        assertEquals(SecureProtocol.ACK_SIZE, ack.size)
        assertArrayEquals(mac(ack.copyOfRange(0, ack.size - 16)), ack.copyOfRange(ack.size - 16, ack.size))
    }
    @Test fun rejectsNegativeSequenceEvenWhenInputMacIsValid() {
        val negativeSequence = input(42L, 99L, -1L)
        assertNull(SecureProtocol.parseInput(negativeSequence, id, 42L, 99L, key))
    }

}
