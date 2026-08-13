package com.quyetbkhoa.phonebridge.shizuku

import com.quyetbkhoa.phonebridge.commands.CommandExecutor
import com.quyetbkhoa.phonebridge.data.ShizukuCommands
import com.quyetbkhoa.phonebridge.model.CommandResult

class ShizukuRemoteStarter(private val commandExecutor: CommandExecutor) {
    suspend fun start(onProgress: (CommandResult) -> Unit = {}): CommandResult =
        commandExecutor.execute(ShizukuCommands.START, onProgress)
}
