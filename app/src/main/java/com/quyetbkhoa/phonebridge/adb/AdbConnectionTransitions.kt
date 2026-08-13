package com.quyetbkhoa.phonebridge.adb

import com.quyetbkhoa.phonebridge.model.AdbConnectionState

object AdbConnectionTransitions {
    fun isAllowed(from: AdbConnectionState, to: AdbConnectionState): Boolean = when (from) {
        AdbConnectionState.Disconnected -> to is AdbConnectionState.Disconnected ||
            to is AdbConnectionState.UsbDetected || to is AdbConnectionState.Connecting || to is AdbConnectionState.Error
        AdbConnectionState.UsbDetected -> to is AdbConnectionState.PermissionRequired ||
            to is AdbConnectionState.Connecting || to is AdbConnectionState.Disconnected || to is AdbConnectionState.Error
        AdbConnectionState.PermissionRequired -> to is AdbConnectionState.Connecting ||
            to is AdbConnectionState.Disconnected || to is AdbConnectionState.Error
        AdbConnectionState.Connecting -> to is AdbConnectionState.Authenticating ||
            to is AdbConnectionState.Connected || to is AdbConnectionState.Disconnected || to is AdbConnectionState.Error
        AdbConnectionState.Authenticating -> to is AdbConnectionState.Authenticating ||
            to is AdbConnectionState.WaitingForAuthorization || to is AdbConnectionState.Connected ||
            to is AdbConnectionState.Disconnected || to is AdbConnectionState.Error
        AdbConnectionState.WaitingForAuthorization -> to is AdbConnectionState.WaitingForAuthorization ||
            to is AdbConnectionState.Connected || to is AdbConnectionState.Disconnected || to is AdbConnectionState.Error
        is AdbConnectionState.Connected -> to is AdbConnectionState.Connected ||
            to is AdbConnectionState.Disconnected || to is AdbConnectionState.Connecting || to is AdbConnectionState.Error
        is AdbConnectionState.Error -> to is AdbConnectionState.Error ||
            to is AdbConnectionState.Disconnected || to is AdbConnectionState.Connecting || to is AdbConnectionState.UsbDetected
    }
}
