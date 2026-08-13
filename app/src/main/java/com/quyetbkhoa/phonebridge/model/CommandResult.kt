package com.quyetbkhoa.phonebridge.model

data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val startedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0
)
