package com.quyetbkhoa.phonebridge.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DeveloperLog {
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(message: String) {
        val safeMessage = message.replace(Regex("(?i)(private[_ -]?key|keystore[_ -]?password)\\s*[:=].*"), "$1=[redacted]")
        val line = "${formatter.format(Date())}  $safeMessage"
        _entries.update { (it + line).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun copyText(): String = entries.value.joinToString("\n")

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
