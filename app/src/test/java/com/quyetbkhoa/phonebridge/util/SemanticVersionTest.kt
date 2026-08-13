package com.quyetbkhoa.phonebridge.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun `compares numeric components rather than strings`() {
        assertTrue(SemanticVersion.parse("0.10.0")!! > SemanticVersion.parse("0.9.0")!!)
        assertTrue(SemanticVersion.parse("v1.0.0")!! > SemanticVersion.parse("1.0.0-rc.2")!!)
        assertTrue(SemanticVersion.parse("1.0.0-rc.10")!! > SemanticVersion.parse("1.0.0-rc.2")!!)
    }

    @Test
    fun `rejects incomplete versions`() {
        assertEquals(null, SemanticVersion.parse("1.2"))
    }
}
