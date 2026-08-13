package com.quyetbkhoa.phonebridge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quyetbkhoa.phonebridge.model.AppSettings
import com.quyetbkhoa.phonebridge.model.CommandHistoryEntry
import com.quyetbkhoa.phonebridge.model.SavedCommand
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.phoneBridgeDataStore by preferencesDataStore("phonebridge")

class PhoneBridgeRepository(
    private val context: Context,
    scope: CoroutineScope
) {
    val settings = context.phoneBridgeDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::settingsFromPreferences)
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val presets: Flow<List<SavedCommand>> = context.phoneBridgeDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodePresets(it[PRESETS]).ifEmpty { listOf(defaultShizukuPreset()) } }

    val history: Flow<List<CommandHistoryEntry>> = context.phoneBridgeDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeHistory(it[HISTORY]) }

    suspend fun setAutoConnect(enabled: Boolean) = edit { it[AUTO_CONNECT] = enabled }

    suspend fun setAutoStartShizuku(enabled: Boolean) = edit { it[AUTO_START_SHIZUKU] = enabled }

    suspend fun setAutoRunPreset(id: String?) = edit {
        if (id == null) it.remove(AUTO_RUN_PRESET) else it[AUTO_RUN_PRESET] = id
    }

    suspend fun savePreset(name: String, command: String, id: String? = null) = edit { preferences ->
        val current = decodePresets(preferences[PRESETS]).ifEmpty { mutableListOf(defaultShizukuPreset()) }.toMutableList()
        val existingIndex = id?.let { value -> current.indexOfFirst { it.id == value } } ?: -1
        val item = SavedCommand(
            id = id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            command = command,
            order = if (existingIndex >= 0) current[existingIndex].order else current.size,
            isBuiltIn = existingIndex >= 0 && current[existingIndex].isBuiltIn
        )
        if (existingIndex >= 0) current[existingIndex] = item else current += item
        preferences[PRESETS] = encodePresets(current)
    }

    suspend fun deletePreset(id: String) = edit { preferences ->
        val current = decodePresets(preferences[PRESETS]).ifEmpty { listOf(defaultShizukuPreset()) }
        if (current.firstOrNull { it.id == id }?.isBuiltIn == true) return@edit
        val updated = current.filterNot { it.id == id }
            .mapIndexed { index, item -> item.copy(order = index) }
        preferences[PRESETS] = encodePresets(updated)
    }

    suspend fun duplicatePreset(id: String) = edit { preferences ->
        val current = decodePresets(preferences[PRESETS]).ifEmpty { mutableListOf(defaultShizukuPreset()) }.toMutableList()
        current.firstOrNull { it.id == id }?.let { original ->
            current += original.copy(
                id = UUID.randomUUID().toString(),
                name = "${original.name} (copy)",
                order = current.size,
                isBuiltIn = false
            )
        }
        preferences[PRESETS] = encodePresets(current)
    }

    suspend fun movePreset(id: String, direction: Int) = edit { preferences ->
        val current = decodePresets(preferences[PRESETS]).ifEmpty { mutableListOf(defaultShizukuPreset()) }
            .sortedBy { it.order }.toMutableList()
        val from = current.indexOfFirst { it.id == id }
        val to = (from + direction).coerceIn(0, current.lastIndex)
        if (from >= 0 && from != to) {
            val item = current.removeAt(from)
            current.add(to, item)
        }
        preferences[PRESETS] = encodePresets(current.mapIndexed { index, item -> item.copy(order = index) })
    }

    suspend fun addHistory(command: String, result: com.quyetbkhoa.phonebridge.model.CommandResult) = edit { preferences ->
        val current = decodeHistory(preferences[HISTORY]).toMutableList()
        current.add(
            0,
            CommandHistoryEntry(
                id = UUID.randomUUID().toString(),
                command = command,
                stdout = result.stdout,
                stderr = result.stderr,
                exitCode = result.exitCode,
                executedAt = result.startedAt,
                durationMs = result.durationMs
            )
        )
        preferences[HISTORY] = encodeHistory(current.take(MAX_HISTORY))
    }

    suspend fun clearHistory() = edit { it.remove(HISTORY) }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.phoneBridgeDataStore.edit { block(it) }
    }

    private fun settingsFromPreferences(preferences: Preferences) = AppSettings(
        autoConnect = preferences[AUTO_CONNECT] ?: false,
        autoStartShizuku = preferences[AUTO_START_SHIZUKU] ?: false,
        autoRunPresetId = preferences[AUTO_RUN_PRESET]
    )

    companion object {
        const val DEFAULT_SHIZUKU_ID = "builtin-start-shizuku"
        private const val MAX_HISTORY = 100
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val AUTO_START_SHIZUKU = booleanPreferencesKey("auto_start_shizuku")
        private val AUTO_RUN_PRESET = stringPreferencesKey("auto_run_preset")
        private val PRESETS = stringPreferencesKey("presets_json")
        private val HISTORY = stringPreferencesKey("history_json")

        fun defaultShizukuPreset() = SavedCommand(
            id = DEFAULT_SHIZUKU_ID,
            name = "Start Shizuku",
            command = ShizukuCommands.START,
            order = 0,
            isBuiltIn = true
        )

        internal fun encodePresets(items: List<SavedCommand>): String = JSONArray().apply {
            items.sortedBy { it.order }.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("command", item.command)
                    put("order", item.order)
                    put("builtIn", item.isBuiltIn)
                })
            }
        }.toString()

        internal fun decodePresets(json: String?): List<SavedCommand> = runCatching {
            val array = JSONArray(json ?: "[]")
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SavedCommand(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    command = item.getString("command"),
                    order = item.optInt("order", index),
                    isBuiltIn = item.optBoolean("builtIn", false)
                )
            }.sortedBy { it.order }
        }.getOrDefault(emptyList())

        internal fun encodeHistory(items: List<CommandHistoryEntry>): String = JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("command", item.command)
                    put("stdout", item.stdout)
                    put("stderr", item.stderr)
                    put("exitCode", item.exitCode ?: JSONObject.NULL)
                    put("executedAt", item.executedAt)
                    put("durationMs", item.durationMs)
                })
            }
        }.toString()

        internal fun decodeHistory(json: String?): List<CommandHistoryEntry> = runCatching {
            val array = JSONArray(json ?: "[]")
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                CommandHistoryEntry(
                    id = item.getString("id"),
                    command = item.getString("command"),
                    stdout = item.optString("stdout"),
                    stderr = item.optString("stderr"),
                    exitCode = if (item.isNull("exitCode")) null else item.getInt("exitCode"),
                    executedAt = item.getLong("executedAt"),
                    durationMs = item.optLong("durationMs")
                )
            }
        }.getOrDefault(emptyList())
    }
}

object ShizukuCommands {
    val START = """
        paths='
        /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
        /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
        '
        for path in ${'$'}paths; do
          if [ -f "${'$'}path" ]; then
            echo "Starting Shizuku with ${'$'}path"
            sh "${'$'}path"
            exit ${'$'}?
          fi
        done
        echo "Shizuku start script was not found in known locations." >&2
        exit 127
    """.trimIndent()
}
