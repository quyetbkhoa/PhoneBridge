package com.quyetbkhoa.phonebridge.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ShellV2Frame(val id: Int, val payload: ByteArray)

class ShellV2Parser {
    private var pending = byteArrayOf()

    fun feed(bytes: ByteArray): List<ShellV2Frame> {
        if (bytes.isNotEmpty()) pending += bytes
        val frames = mutableListOf<ShellV2Frame>()
        var offset = 0
        while (pending.size - offset >= HEADER_SIZE) {
            val id = pending[offset].toInt() and 0xff
            val length = ByteBuffer.wrap(pending, offset + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
            require(length in 0..MAX_FRAME_SIZE) { "Invalid shell,v2 frame length: $length" }
            if (pending.size - offset - HEADER_SIZE < length) break
            frames += ShellV2Frame(id, pending.copyOfRange(offset + HEADER_SIZE, offset + HEADER_SIZE + length))
            offset += HEADER_SIZE + length
        }
        if (offset > 0) pending = pending.copyOfRange(offset, pending.size)
        return frames
    }

    fun hasPendingBytes(): Boolean = pending.isNotEmpty()

    companion object {
        const val STDIN = 0
        const val STDOUT = 1
        const val STDERR = 2
        const val EXIT = 3
        const val CLOSE_STDIN = 4
        private const val HEADER_SIZE = 5
        private const val MAX_FRAME_SIZE = 16 * 1024 * 1024

        fun encode(id: Int, payload: ByteArray = byteArrayOf()): ByteArray =
            ByteBuffer.allocate(HEADER_SIZE + payload.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(id.toByte())
                .putInt(payload.size)
                .put(payload)
                .array()
    }
}
