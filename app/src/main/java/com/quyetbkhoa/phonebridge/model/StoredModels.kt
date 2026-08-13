package com.quyetbkhoa.phonebridge.model

data class SavedCommand(
    val id: String,
    val name: String,
    val command: String,
    val order: Int,
    val isBuiltIn: Boolean = false
)

data class CommandHistoryEntry(
    val id: String,
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val executedAt: Long,
    val durationMs: Long
)

data class AppSettings(
    val autoConnect: Boolean = false,
    val autoStartShizuku: Boolean = false,
    val autoRunPresetId: String? = null
)
