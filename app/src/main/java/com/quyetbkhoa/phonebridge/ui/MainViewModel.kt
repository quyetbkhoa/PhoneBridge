package com.quyetbkhoa.phonebridge.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quyetbkhoa.phonebridge.PhoneBridgeApplication
import com.quyetbkhoa.phonebridge.model.CommandResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as PhoneBridgeApplication).graph
    val connectionState = graph.usbController.state
    val presets = graph.repository.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = graph.repository.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = graph.repository.settings
    val logs = graph.log.entries
    val updateState = graph.updater.state

    private val _terminalText = MutableStateFlow("")
    val terminalText: StateFlow<String> = _terminalText.asStateFlow()
    private val _lastResult = MutableStateFlow<CommandResult?>(null)
    val lastResult: StateFlow<CommandResult?> = _lastResult.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _longRunning = MutableStateFlow(false)
    val longRunning: StateFlow<Boolean> = _longRunning.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var commandJob: Job? = null

    fun connect() = graph.usbController.connect()
    fun disconnect() = graph.usbController.disconnect()

    fun setTerminalText(value: String) {
        _terminalText.value = value
    }

    fun clearTerminal() {
        _terminalText.value = ""
        _lastResult.value = null
    }

    fun runTerminal() = runCommand(_terminalText.value)

    fun runCommand(command: String) {
        if (command.isBlank() || _running.value) return
        commandJob = viewModelScope.launch {
            _running.value = true
            _longRunning.value = false
            _lastResult.value = null
            val hintJob = launch {
                delay(LONG_RUNNING_HINT_MS)
                _longRunning.value = true
            }
            try {
                _lastResult.value = graph.commandExecutor.execute(command) { progress ->
                    _lastResult.value = progress
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _lastResult.value = CommandResult("", error.message ?: "Command failed", null)
            } finally {
                hintJob.cancel()
                _running.value = false
                _longRunning.value = false
            }
        }
    }

    fun stopCommand() {
        commandJob?.cancel()
        graph.usbController.stopCommand()
        _running.value = false
        _longRunning.value = false
        _message.value = "Command stopped; USB connection was closed."
    }

    fun startShizuku() {
        if (_running.value) return
        commandJob = viewModelScope.launch {
            _running.value = true
            _longRunning.value = false
            _lastResult.value = null
            val hintJob = launch {
                delay(LONG_RUNNING_HINT_MS)
                _longRunning.value = true
            }
            try {
                _lastResult.value = graph.shizukuStarter.start { progress ->
                    _lastResult.value = progress
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _lastResult.value = CommandResult("", error.message ?: "Unable to start Shizuku", null)
            } finally {
                hintJob.cancel()
                _running.value = false
                _longRunning.value = false
            }
        }
    }

    fun saveTerminalAsPreset(name: String) {
        val command = _terminalText.value
        if (name.isBlank() || command.isBlank()) return
        viewModelScope.launch {
            graph.repository.savePreset(name, command)
            _message.value = "Preset saved"
        }
    }

    fun savePreset(name: String, command: String, id: String? = null) {
        if (name.isBlank() || command.isBlank()) return
        viewModelScope.launch { graph.repository.savePreset(name, command, id) }
    }

    fun deletePreset(id: String) = viewModelScope.launch { graph.repository.deletePreset(id) }
    fun duplicatePreset(id: String) = viewModelScope.launch { graph.repository.duplicatePreset(id) }
    fun movePreset(id: String, direction: Int) = viewModelScope.launch { graph.repository.movePreset(id, direction) }

    fun importScript(uri: Uri) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (name != null && !name.endsWith(".sh", ignoreCase = true)) {
                    throw IOException("Please select a .sh file")
                }
                val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("Unable to read script")
                if (content.length > MAX_SCRIPT_CHARS) throw IOException("Script is too large")
                _terminalText.value = content
                _message.value = "Imported ${name ?: "script"}"
            } catch (error: Throwable) {
                _message.value = error.message ?: "Script import failed"
            }
        }
    }

    fun runAndSaveScript(name: String) {
        saveTerminalAsPreset(name)
        runTerminal()
    }

    fun setAutoConnect(value: Boolean) = viewModelScope.launch { graph.repository.setAutoConnect(value) }
    fun setAutoStartShizuku(value: Boolean) = viewModelScope.launch { graph.repository.setAutoStartShizuku(value) }
    fun setAutoRunPreset(id: String?) = viewModelScope.launch { graph.repository.setAutoRunPreset(id) }
    fun clearHistory() = viewModelScope.launch { graph.repository.clearHistory() }
    fun clearLogs() = graph.log.clear()
    fun copyLogs(): String = graph.log.copyText()
    fun checkUpdate() = graph.updater.check()
    fun installUpdate(release: com.quyetbkhoa.phonebridge.update.GitHubRelease) = graph.updater.downloadAndInstall(release)
    fun installVerified(apk: java.io.File) = graph.updater.installVerified(apk)
    fun consumeMessage() { _message.value = null }

    companion object {
        private const val MAX_SCRIPT_CHARS = 2 * 1024 * 1024
        private const val LONG_RUNNING_HINT_MS = 8_000L
    }
}
