package com.quyetbkhoa.phonebridge.util

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun `matches known SHA-256 vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray())
        )
    }
}
