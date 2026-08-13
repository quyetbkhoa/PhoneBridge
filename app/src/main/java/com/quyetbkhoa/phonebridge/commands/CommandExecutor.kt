package com.quyetbkhoa.phonebridge.commands

import com.quyetbkhoa.phonebridge.adb.AdbConnection
import com.quyetbkhoa.phonebridge.data.PhoneBridgeRepository
import com.quyetbkhoa.phonebridge.model.CommandResult

class CommandExecutor(
    private val adbConnection: AdbConnection,
    private val repository: PhoneBridgeRepository
) {
    suspend fun execute(command: String): CommandResult {
        val result = adbConnection.execute(command)
        repository.addHistory(command, result)
        return result
    }
}
