package com.quyetbkhoa.phonebridge.util

object ShellScripts {
    fun legacyCommand(script: String): String {
        var delimiter = "PHONEBRIDGE_EOF"
        var suffix = 0
        while (script.lineSequence().any { it == delimiter }) {
            suffix += 1
            delimiter = "PHONEBRIDGE_EOF_$suffix"
        }
        return "sh <<'$delimiter'\n$script\n$delimiter"
    }
}
