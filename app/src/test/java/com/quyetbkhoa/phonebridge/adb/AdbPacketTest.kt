package com.quyetbkhoa.phonebridge.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdbPacketTest {
    @Test
    fun `packet round trips with checksum and magic`() {
        val original = AdbPacket(AdbProtocol.OPEN, 42, 0, "shell:id\u0000".toByteArray())
        val encoded = original.encode()
        val decoded = AdbPacket.decode(encoded.copyOfRange(0, 24), encoded.copyOfRange(24, encoded.size))

        assertEquals(original.command, decoded.command)
        assertEquals(original.arg0, decoded.arg0)
        assertArrayEquals(original.payload, decoded.payload)
        assertEquals(original.payload.sumOf { it.toInt() and 0xff }, AdbPacket.checksum(original.payload))
    }

    @Test
    fun `invalid checksum is rejected`() {
        val encoded = AdbPacket(AdbProtocol.WRTE, 1, 2, byteArrayOf(1, 2, 3)).encode()
        val header = encoded.copyOfRange(0, 24)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(16, 999)
        assertThrows(IllegalArgumentException::class.java) {
            AdbPacket.decode(header, encoded.copyOfRange(24, encoded.size))
        }
    }

    @Test
    fun `version 1 point 1 packet may skip checksum`() {
        val encoded = AdbPacket(AdbProtocol.WRTE, 1, 2, "output".toByteArray()).encode(skipChecksum = true)
        val header = encoded.copyOfRange(0, 24)
        val decoded = AdbPacket.decode(header, encoded.copyOfRange(24, encoded.size))

        assertEquals(0, ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(16))
        assertArrayEquals("output".toByteArray(), decoded.payload)
    }

    @Test
    fun `ADB command constants match wire ASCII`() {
        assertEquals("CNXN", AdbProtocol.commandName(AdbProtocol.CNXN))
        assertEquals("AUTH", AdbProtocol.commandName(AdbProtocol.AUTH))
        assertEquals("OPEN", AdbProtocol.commandName(AdbProtocol.OPEN))
        assertEquals("OKAY", AdbProtocol.commandName(AdbProtocol.OKAY))
        assertEquals("WRTE", AdbProtocol.commandName(AdbProtocol.WRTE))
        assertEquals("CLSE", AdbProtocol.commandName(AdbProtocol.CLSE))
    }
}
