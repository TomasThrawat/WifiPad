package com.wifipad.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolPacketTest {
    private fun validPacket() = byteArrayOf(
        Protocol.MAGIC, Protocol.VERSION, 0x01, 0x04,
        0x7f, 0x81.toByte(), 0x80.toByte(), 0x80.toByte(),
        0x80.toByte(), 0xff.toByte(), 0x08
    )

    @Test fun decodesV1AndIgnoresLegacyRightStickBytes() {
        val parsed = ProtocolPacket.parse(validPacket())!!
        assertEquals(0x401, parsed.buttons)
        assertEquals(127, parsed.leftX)
        assertEquals(-127, parsed.leftY)
        assertEquals(128, parsed.leftTrigger)
        assertEquals(255, parsed.rightTrigger)
        assertEquals(8, parsed.dpad)
    }

    @Test fun requiresExactLengthAndCorrectHeader() {
        val packet = validPacket()
        assertNull(ProtocolPacket.parse(packet.copyOf(10)))
        assertNull(ProtocolPacket.parse(packet + 0))
        assertNull(ProtocolPacket.parse(packet.apply { this[0] = 0 }))
        packet[0] = Protocol.MAGIC
        packet[1] = 2
        assertNull(ProtocolPacket.parse(packet))
    }

    @Test fun rejectsReservedButtonsInvalidStickAndDpadValues() {
        assertNull(ProtocolPacket.parse(validPacket().apply { this[3] = 0x08 }))
        assertNull(ProtocolPacket.parse(validPacket().apply { this[4] = 0x80.toByte() }))
        assertNull(ProtocolPacket.parse(validPacket().apply { this[5] = 0x80.toByte() }))
        assertNull(ProtocolPacket.parse(validPacket().apply { this[10] = 9 }))
    }

    @Test fun supportsDatagramOffset() {
        val wrapped = byteArrayOf(0, 0) + validPacket() + byteArrayOf(0)
        assertNotNull(ProtocolPacket.parse(wrapped, 2, Protocol.PACKET_SIZE))
        assertNull(ProtocolPacket.parse(wrapped, 2, Protocol.PACKET_SIZE - 1))
    }
}
