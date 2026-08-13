package com.quyetbkhoa.phonebridge.adb

object AdbProtocol {
    const val VERSION = 0x01000001
    const val MAX_PAYLOAD = 1024 * 1024

    const val CNXN = 0x4e584e43
    const val AUTH = 0x48545541
    const val OPEN = 0x4e45504f
    const val OKAY = 0x59414b4f
    const val CLSE = 0x45534c43
    const val WRTE = 0x45545257

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    fun commandName(command: Int): String = when (command) {
        CNXN -> "CNXN"
        AUTH -> "AUTH"
        OPEN -> "OPEN"
        OKAY -> "OKAY"
        CLSE -> "CLSE"
        WRTE -> "WRTE"
        else -> "0x${command.toUInt().toString(16)}"
    }
}
