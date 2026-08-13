package com.quyetbkhoa.phonebridge.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.io.Closeable
import java.io.IOException

class UsbTransport private constructor(
    private val target: UsbAdbTarget,
    private val connection: UsbDeviceConnection
) : Closeable {
    @Volatile
    private var closed = false

    fun writeFully(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            checkOpen()
            val count = minOf(USB_CHUNK_SIZE, bytes.size - offset)
            val written = connection.bulkTransfer(target.bulkOut, bytes, offset, count, WRITE_TIMEOUT_MS)
            if (written < 0) throw IOException("USB bulk OUT failed")
            if (written == 0) continue
            offset += written
        }
    }

    fun readExactly(length: Int): ByteArray {
        require(length >= 0)
        val output = ByteArray(length)
        var offset = 0
        while (offset < length) {
            checkOpen()
            val count = minOf(USB_CHUNK_SIZE, length - offset)
            val read = connection.bulkTransfer(target.bulkIn, output, offset, count, READ_TIMEOUT_MS)
            if (read < 0) throw IOException("USB bulk IN failed")
            if (read == 0) continue
            offset += read
        }
        return output
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(target.usbInterface) }
        connection.close()
    }

    private fun checkOpen() {
        if (closed) throw IOException("USB transport is closed")
    }

    companion object {
        private const val USB_CHUNK_SIZE = 16 * 1024
        private const val READ_TIMEOUT_MS = 0
        private const val WRITE_TIMEOUT_MS = 5_000

        fun open(usbManager: UsbManager, target: UsbAdbTarget): UsbTransport {
            check(usbManager.hasPermission(target.device)) { "USB permission not granted" }
            val connection = usbManager.openDevice(target.device)
                ?: error("Unable to open USB device")
            if (!connection.claimInterface(target.usbInterface, true)) {
                connection.close()
                error("Unable to claim ADB USB interface")
            }
            return UsbTransport(target, connection)
        }
    }
}
