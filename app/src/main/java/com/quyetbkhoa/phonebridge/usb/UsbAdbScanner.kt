package com.quyetbkhoa.phonebridge.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

data class UsbAdbTarget(
    val device: UsbDevice,
    val usbInterface: UsbInterface,
    val bulkIn: UsbEndpoint,
    val bulkOut: UsbEndpoint
)

class UsbAdbScanner(private val usbManager: UsbManager) {
    fun scan(): List<UsbAdbTarget> = usbManager.deviceList.values.mapNotNull(::findAdbTarget)

    fun findAdbTarget(device: UsbDevice): UsbAdbTarget? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)
            if (usbInterface.interfaceClass != ADB_CLASS ||
                usbInterface.interfaceSubclass != ADB_SUBCLASS ||
                usbInterface.interfaceProtocol != ADB_PROTOCOL
            ) continue

            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                when (endpoint.direction) {
                    UsbConstants.USB_DIR_IN -> bulkIn = endpoint
                    UsbConstants.USB_DIR_OUT -> bulkOut = endpoint
                }
            }
            if (bulkIn != null && bulkOut != null) {
                return UsbAdbTarget(device, usbInterface, bulkIn, bulkOut)
            }
        }
        return null
    }

    companion object {
        const val ADB_CLASS = 0xff
        const val ADB_SUBCLASS = 0x42
        const val ADB_PROTOCOL = 0x01
    }
}
