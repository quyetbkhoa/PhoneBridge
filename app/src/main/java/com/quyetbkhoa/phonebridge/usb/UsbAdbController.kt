package com.quyetbkhoa.phonebridge.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.quyetbkhoa.phonebridge.adb.AdbConnection
import com.quyetbkhoa.phonebridge.commands.CommandExecutor
import com.quyetbkhoa.phonebridge.data.PhoneBridgeRepository
import com.quyetbkhoa.phonebridge.logging.DeveloperLog
import com.quyetbkhoa.phonebridge.model.AdbConnectionState
import com.quyetbkhoa.phonebridge.shizuku.ShizukuRemoteStarter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UsbAdbController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adbConnection: AdbConnection,
    private val repository: PhoneBridgeRepository,
    private val commandExecutor: CommandExecutor,
    private val shizukuStarter: ShizukuRemoteStarter,
    private val log: DeveloperLog
) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private val scanner = UsbAdbScanner(usbManager)
    private val connecting = AtomicBoolean(false)
    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val state: StateFlow<AdbConnectionState> = _state.asStateFlow()
    private var connectJob: Job? = null
    private var activeDeviceId: Int? = null
    private var autoExecutedDeviceId: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> handlePermissionResult(intent)
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> usbDevice(intent)?.let(::handleAttached)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> usbDevice(intent)?.let(::handleDetached)
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            adbConnection.state.collect { adbState ->
                val usbOnlyState = _state.value is AdbConnectionState.UsbDetected ||
                    _state.value is AdbConnectionState.PermissionRequired
                if (adbState !is AdbConnectionState.Disconnected || !usbOnlyState) {
                    _state.value = adbState
                }
            }
        }
        refreshUsbState()
    }

    fun connect() {
        val target = scanner.scan().firstOrNull()
        if (target == null) {
            _state.value = AdbConnectionState.Error(NO_HOST_MESSAGE)
            log.add("No ADB USB device detected")
            return
        }
        log.add("ADB interface found")
        _state.value = AdbConnectionState.UsbDetected
        if (!usbManager.hasPermission(target.device)) {
            _state.value = AdbConnectionState.PermissionRequired
            requestPermission(target.device)
        } else {
            connectTarget(target)
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        connecting.set(false)
        activeDeviceId = null
        adbConnection.disconnect()
    }

    fun stopCommand() = disconnect()

    fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbDevice(intent)?.let(::handleAttached)
        }
    }

    private fun refreshUsbState() {
        val target = scanner.scan().firstOrNull() ?: return
        log.add("USB device detected")
        _state.value = AdbConnectionState.UsbDetected
        if (repository.settings.value.autoConnect) connect()
    }

    private fun handleAttached(device: UsbDevice) {
        val target = scanner.findAdbTarget(device) ?: return
        log.add("USB device detected: ${device.deviceName}")
        _state.value = AdbConnectionState.UsbDetected
        if (repository.settings.value.autoConnect) {
            if (usbManager.hasPermission(device)) connectTarget(target) else requestPermission(device)
        }
    }

    private fun handleDetached(device: UsbDevice) {
        if (device.deviceId != activeDeviceId) return
        log.add("USB device detached")
        autoExecutedDeviceId = null
        disconnect()
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, intent)
        log.add("USB permission requested")
    }

    private fun handlePermissionResult(intent: Intent) {
        val device = usbDevice(intent) ?: return
        if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            _state.value = AdbConnectionState.Error("USB permission denied")
            log.add("USB permission denied")
            return
        }
        log.add("USB permission granted")
        scanner.findAdbTarget(device)?.let(::connectTarget)
    }

    private fun connectTarget(target: UsbAdbTarget) {
        if (!connecting.compareAndSet(false, true)) return
        activeDeviceId = target.device.deviceId
        connectJob = scope.launch {
            try {
                val transport = UsbTransport.open(usbManager, target)
                adbConnection.connect(transport)
                runAutomaticActionsOnce(target.device.deviceId)
            } catch (_: Throwable) {
                // AdbConnection publishes the actionable error state.
            } finally {
                connecting.set(false)
            }
        }
    }

    private suspend fun runAutomaticActionsOnce(deviceId: Int) {
        if (autoExecutedDeviceId == deviceId) return
        autoExecutedDeviceId = deviceId
        val settings = repository.settings.value
        if (settings.autoStartShizuku) shizukuStarter.start()
        val presetId = settings.autoRunPresetId ?: return
        val preset = repository.presets.first().firstOrNull { it.id == presetId }
        if (preset != null) commandExecutor.execute(preset.command)
    }

    @Suppress("DEPRECATION")
    private fun usbDevice(intent: Intent): UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    } else {
        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.quyetbkhoa.phonebridge.USB_PERMISSION"
        const val NO_HOST_MESSAGE = "No ADB USB device detected. This phone may not currently be the USB host. Check USB connection mode on both phones."
    }
}
