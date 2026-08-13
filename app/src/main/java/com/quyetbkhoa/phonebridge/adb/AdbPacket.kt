package com.quyetbkhoa.phonebridge.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AdbPacket(
    val command: Int,
    val arg0: Int = 0,
    val arg1: Int = 0,
    val payload: ByteArray = byteArrayOf()
) {
    fun encode(skipChecksum: Boolean = false): ByteArray {
        val result = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        result.putInt(command)
        result.putInt(arg0)
        result.putInt(arg1)
        result.putInt(payload.size)
        result.putInt(if (skipChecksum) 0 else checksum(payload))
        result.putInt(command xor -0x1)
        result.put(payload)
        return result.array()
    }

    companion object {
        const val HEADER_SIZE = 24

        fun decode(header: ByteArray, payload: ByteArray): AdbPacket {
            require(header.size == HEADER_SIZE) { "ADB header must be $HEADER_SIZE bytes" }
            val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = data.int
            val arg0 = data.int
            val arg1 = data.int
            val length = data.int
            val expectedChecksum = data.int
            val magic = data.int
            require(magic == command xor -0x1) { "Invalid ADB command magic" }
            require(length == payload.size) { "ADB payload length mismatch" }
            require(expectedChecksum == 0 || expectedChecksum == checksum(payload)) { "Invalid ADB payload checksum" }
            return AdbPacket(command, arg0, arg1, payload)
        }

        fun payloadLength(header: ByteArray): Int {
            require(header.size == HEADER_SIZE)
            return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        }

        fun checksum(payload: ByteArray): Int = payload.fold(0) { sum, byte -> sum + (byte.toInt() and 0xff) }
    }
}
