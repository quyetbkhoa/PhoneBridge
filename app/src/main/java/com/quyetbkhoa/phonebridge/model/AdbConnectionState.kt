package com.quyetbkhoa.phonebridge.model

sealed interface AdbConnectionState {
    data object Disconnected : AdbConnectionState
    data object UsbDetected : AdbConnectionState
    data object PermissionRequired : AdbConnectionState
    data object Connecting : AdbConnectionState
    data object Authenticating : AdbConnectionState
    data object WaitingForAuthorization : AdbConnectionState
    data class Connected(val device: RemoteDeviceInfo) : AdbConnectionState
    data class Error(val message: String) : AdbConnectionState
}
