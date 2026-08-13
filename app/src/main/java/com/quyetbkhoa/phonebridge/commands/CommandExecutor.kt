package com.quyetbkhoa.phonebridge.commands

import com.quyetbkhoa.phonebridge.adb.AdbConnection
import com.quyetbkhoa.phonebridge.data.PhoneBridgeRepository
import com.quyetbkhoa.phonebridge.model.CommandResult

class CommandExecutor(
    private val adbConnection: AdbConnection,
    private val repository: PhoneBridgeRepository
) {
    suspend fun execute(
        command: String,
        onProgress: (CommandResult) -> Unit = {}
    ): CommandResult {
        val result = adbConnection.execute(command, onProgress)
        repository.addHistory(command, result)
        return result
    }
}
