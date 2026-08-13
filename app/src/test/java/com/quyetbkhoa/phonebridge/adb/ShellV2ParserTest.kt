package com.quyetbkhoa.phonebridge.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShellV2ParserTest {
    @Test
    fun `parses fragmented stdout stderr and exit frames`() {
        val bytes = ShellV2Parser.encode(ShellV2Parser.STDOUT, "hello".toByteArray()) +
            ShellV2Parser.encode(ShellV2Parser.STDERR, "warning".toByteArray()) +
            ShellV2Parser.encode(ShellV2Parser.EXIT, byteArrayOf(7))
        val parser = ShellV2Parser()

        val frames = buildList {
            addAll(parser.feed(bytes.copyOfRange(0, 3)))
            addAll(parser.feed(bytes.copyOfRange(3, 11)))
            addAll(parser.feed(bytes.copyOfRange(11, bytes.size)))
        }

        assertEquals(listOf(1, 2, 3), frames.map { it.id })
        assertArrayEquals("hello".toByteArray(), frames[0].payload)
        assertArrayEquals("warning".toByteArray(), frames[1].payload)
        assertEquals(7, frames[2].payload.single().toInt())
        assertFalse(parser.hasPendingBytes())
    }
}
