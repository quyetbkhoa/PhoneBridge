package com.quyetbkhoa.phonebridge.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ShellScriptsTest {
    @Test
    fun `chooses a heredoc delimiter not present in script`() {
        val result = ShellScripts.legacyCommand("echo before\nPHONEBRIDGE_EOF\necho after")
        assertTrue(result.startsWith("sh <<'PHONEBRIDGE_EOF_1'"))
        assertTrue(result.endsWith("PHONEBRIDGE_EOF_1"))
    }
}
