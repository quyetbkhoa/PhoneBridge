package com.quyetbkhoa.phonebridge.model

data class RemoteDeviceInfo(
    val manufacturer: String = "Unknown",
    val model: String = "Unknown",
    val androidVersion: String = "Unknown",
    val sdk: String = "Unknown",
    val fingerprint: String = "Unknown"
)
