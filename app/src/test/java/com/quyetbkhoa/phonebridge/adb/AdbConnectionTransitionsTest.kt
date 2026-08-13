package com.quyetbkhoa.phonebridge.adb

import com.quyetbkhoa.phonebridge.model.AdbConnectionState
import com.quyetbkhoa.phonebridge.model.RemoteDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbConnectionTransitionsTest {
    @Test
    fun `authorization state follows valid connection path`() {
        assertTrue(AdbConnectionTransitions.isAllowed(AdbConnectionState.Disconnected, AdbConnectionState.Connecting))
        assertTrue(AdbConnectionTransitions.isAllowed(AdbConnectionState.Connecting, AdbConnectionState.Authenticating))
        assertTrue(AdbConnectionTransitions.isAllowed(AdbConnectionState.Authenticating, AdbConnectionState.WaitingForAuthorization))
        assertTrue(
            AdbConnectionTransitions.isAllowed(
                AdbConnectionState.WaitingForAuthorization,
                AdbConnectionState.Connected(RemoteDeviceInfo())
            )
        )
    }

    @Test
    fun `disconnected cannot jump directly to connected`() {
        assertFalse(
            AdbConnectionTransitions.isAllowed(
                AdbConnectionState.Disconnected,
                AdbConnectionState.Connected(RemoteDeviceInfo())
            )
        )
    }
}
