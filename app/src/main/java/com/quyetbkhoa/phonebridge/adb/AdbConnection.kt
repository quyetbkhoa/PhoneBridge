package com.quyetbkhoa.phonebridge.adb

import com.quyetbkhoa.phonebridge.logging.DeveloperLog
import com.quyetbkhoa.phonebridge.model.AdbConnectionState
import com.quyetbkhoa.phonebridge.model.CommandResult
import com.quyetbkhoa.phonebridge.model.RemoteDeviceInfo
import com.quyetbkhoa.phonebridge.usb.UsbTransport
import com.quyetbkhoa.phonebridge.util.ShellScripts
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AdbConnection(
    private val crypto: AdbCrypto,
    private val log: DeveloperLog
) {
    private val commandMutex = Mutex()
    private val nextLocalId = AtomicInteger(1)
    private val _state = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val state: StateFlow<AdbConnectionState> = _state.asStateFlow()

    @Volatile
    private var transport: UsbTransport? = null
    @Volatile
    private var connected = false
    private var maxPayload = AdbProtocol.MAX_PAYLOAD
    private var skipChecksum = false

    suspend fun connect(newTransport: UsbTransport) = withContext(Dispatchers.IO) {
        disconnect()
        skipChecksum = false
        transport = newTransport
        publish(AdbConnectionState.Connecting)
        try {
            send(
                AdbPacket(
                    AdbProtocol.CNXN,
                    AdbProtocol.VERSION,
                    AdbProtocol.MAX_PAYLOAD,
                    "host::features=shell_v2\u0000".toByteArray()
                )
            )
            log.add("CNXN sent")
            var signatureSent = false
            var publicKeySent = false
            while (!connected) {
                val packet = receive()
                when (packet.command) {
                    AdbProtocol.AUTH -> {
                        require(packet.arg0 == AdbProtocol.AUTH_TOKEN) { "Unsupported ADB AUTH request" }
                        log.add("AUTH received")
                        publish(AdbConnectionState.Authenticating)
                        if (!signatureSent) {
                            send(AdbPacket(AdbProtocol.AUTH, AdbProtocol.AUTH_SIGNATURE, 0, crypto.signToken(packet.payload)))
                            signatureSent = true
                            log.add("RSA signature sent")
                        } else if (!publicKeySent) {
                            send(AdbPacket(AdbProtocol.AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, crypto.publicKeyPayload()))
                            publicKeySent = true
                            publish(AdbConnectionState.WaitingForAuthorization)
                            log.add("RSA public key sent; waiting for authorization")
                        } else {
                            publish(AdbConnectionState.WaitingForAuthorization)
                        }
                    }
                    AdbProtocol.CNXN -> {
                        maxPayload = packet.arg1.coerceIn(AdbPacket.HEADER_SIZE, AdbProtocol.MAX_PAYLOAD)
                        skipChecksum = packet.arg0 >= AdbProtocol.VERSION
                        connected = true
                        log.add("ADB connected")
                    }
                    else -> log.add("Ignoring ${AdbProtocol.commandName(packet.command)} during handshake")
                }
            }
            publish(AdbConnectionState.Connected(RemoteDeviceInfo()))
            val info = readRemoteDeviceInfo()
            publish(AdbConnectionState.Connected(info))
        } catch (cancelled: CancellationException) {
            disconnect()
            throw cancelled
        } catch (error: Throwable) {
            disconnect()
            publish(AdbConnectionState.Error(error.message ?: "ADB connection failed"))
            log.add("ADB connection error: ${error.message}")
            throw error
        }
    }

    suspend fun execute(script: String): CommandResult = commandMutex.withLock {
        require(script.isNotBlank()) { "Command is empty" }
        check(connected) { "ADB is not connected" }
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val result = try {
                executeShellV2(script)
            } catch (rejected: ServiceRejectedException) {
                log.add("shell,v2 unavailable; falling back to legacy shell")
                executeLegacyShell(script)
            }
            result.copy(startedAt = startedAt, durationMs = System.currentTimeMillis() - startedAt)
        }
    }

    fun disconnect() {
        connected = false
        transport?.close()
        transport = null
        if (_state.value !is AdbConnectionState.Disconnected) {
            publish(AdbConnectionState.Disconnected)
        }
        log.add("ADB disconnected")
    }

    private suspend fun readRemoteDeviceInfo(): RemoteDeviceInfo {
        val command = listOf(
            "getprop ro.product.manufacturer",
            "getprop ro.product.model",
            "getprop ro.build.version.release",
            "getprop ro.build.version.sdk",
            "getprop ro.build.fingerprint"
        ).joinToString("\n")
        return runCatching { execute(command).stdout.lines() }.fold(
            onSuccess = { lines ->
                RemoteDeviceInfo(
                    manufacturer = lines.getOrNull(0).orEmpty().ifBlank { "Unknown" },
                    model = lines.getOrNull(1).orEmpty().ifBlank { "Unknown" },
                    androidVersion = lines.getOrNull(2).orEmpty().ifBlank { "Unknown" },
                    sdk = lines.getOrNull(3).orEmpty().ifBlank { "Unknown" },
                    fingerprint = lines.getOrNull(4).orEmpty().ifBlank { "Unknown" }
                )
            },
            onFailure = {
                log.add("Unable to read remote information: ${it.message}")
                RemoteDeviceInfo()
            }
        )
    }

    private fun executeShellV2(script: String): CommandResult {
        val stream = openStream("shell,v2,raw:sh")
        log.add("OPEN shell,v2")
        val parser = ShellV2Parser()
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        var exitCode: Int? = null

        fun accept(packet: AdbPacket) {
            for (frame in parser.feed(packet.payload)) {
                when (frame.id) {
                    ShellV2Parser.STDOUT -> stdout.write(frame.payload)
                    ShellV2Parser.STDERR -> stderr.write(frame.payload)
                    ShellV2Parser.EXIT -> exitCode = frame.payload.firstOrNull()?.toInt()?.and(0xff)
                }
            }
        }

        val input = script.toByteArray(Charsets.UTF_8).let { if (script.endsWith('\n')) it else it + '\n'.code.toByte() }
        var offset = 0
        val chunkSize = (maxPayload - 5).coerceAtLeast(1)
        while (offset < input.size) {
            val end = minOf(input.size, offset + chunkSize)
            sendWriteAndAwaitOkay(stream, ShellV2Parser.encode(ShellV2Parser.STDIN, input.copyOfRange(offset, end)), ::accept)
            offset = end
        }
        sendWriteAndAwaitOkay(stream, ShellV2Parser.encode(ShellV2Parser.CLOSE_STDIN), ::accept)
        readUntilClosed(stream, ::accept)
        if (parser.hasPendingBytes()) throw IOException("Truncated shell,v2 frame")
        return CommandResult(stdout.toString(Charsets.UTF_8.name()), stderr.toString(Charsets.UTF_8.name()), exitCode)
    }

    private fun executeLegacyShell(script: String): CommandResult {
        val stream = openStream("shell:${ShellScripts.legacyCommand(script)}")
        log.add("OPEN legacy shell")
        val stdout = ByteArrayOutputStream()
        readUntilClosed(stream) { stdout.write(it.payload) }
        return CommandResult(stdout.toString(Charsets.UTF_8.name()), "", null)
    }

    private fun openStream(service: String): StreamIds {
        val localId = nextLocalId.getAndIncrement().let { if (it == 0) nextLocalId.getAndIncrement() else it }
        send(AdbPacket(AdbProtocol.OPEN, localId, 0, "$service\u0000".toByteArray()))
        while (true) {
            val response = receive()
            when {
                response.command == AdbProtocol.OKAY && response.arg1 == localId -> return StreamIds(localId, response.arg0)
                response.command == AdbProtocol.CLSE && response.arg1 == localId -> throw ServiceRejectedException(service)
                else -> log.add("Unexpected ${AdbProtocol.commandName(response.command)} while opening stream")
            }
        }
    }

    private fun sendWriteAndAwaitOkay(stream: StreamIds, payload: ByteArray, onWrite: (AdbPacket) -> Unit) {
        send(AdbPacket(AdbProtocol.WRTE, stream.local, stream.remote, payload))
        while (true) {
            val response = receive()
            when {
                response.command == AdbProtocol.OKAY && response.arg1 == stream.local -> return
                response.command == AdbProtocol.WRTE && response.arg1 == stream.local -> {
                    onWrite(response)
                    send(AdbPacket(AdbProtocol.OKAY, stream.local, stream.remote))
                }
                response.command == AdbProtocol.CLSE && response.arg1 == stream.local -> {
                    throw IOException("ADB shell closed while sending input")
                }
            }
        }
    }

    private fun readUntilClosed(stream: StreamIds, onWrite: (AdbPacket) -> Unit) {
        while (true) {
            val response = receive()
            when {
                response.command == AdbProtocol.WRTE && response.arg1 == stream.local -> {
                    log.add("WRTE received (${response.payload.size} bytes)")
                    onWrite(response)
                    send(AdbPacket(AdbProtocol.OKAY, stream.local, stream.remote))
                }
                response.command == AdbProtocol.CLSE && response.arg1 == stream.local -> {
                    log.add("Command complete")
                    return
                }
            }
        }
    }

    private fun send(packet: AdbPacket) {
        transportOrThrow().writeFully(packet.encode(skipChecksum))
        if (packet.command != AdbProtocol.WRTE) log.add("${AdbProtocol.commandName(packet.command)} sent")
    }

    private fun receive(): AdbPacket {
        val transport = transportOrThrow()
        val header = transport.readExactly(AdbPacket.HEADER_SIZE)
        val payloadLength = AdbPacket.payloadLength(header)
        if (payloadLength !in 0..AdbProtocol.MAX_PAYLOAD) throw IOException("Invalid ADB payload length: $payloadLength")
        val payload = transport.readExactly(payloadLength)
        return AdbPacket.decode(header, payload)
    }

    private fun transportOrThrow(): UsbTransport = transport ?: throw IOException("USB transport is unavailable")

    private fun publish(next: AdbConnectionState) {
        check(AdbConnectionTransitions.isAllowed(_state.value, next)) {
            "Invalid ADB state transition: ${_state.value} -> $next"
        }
        _state.value = next
    }

    private data class StreamIds(val local: Int, val remote: Int)
    private class ServiceRejectedException(service: String) : IOException("ADB service rejected: $service")
}
