package com.quyetbkhoa.phonebridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quyetbkhoa.phonebridge.BuildConfig
import com.quyetbkhoa.phonebridge.model.AdbConnectionState
import com.quyetbkhoa.phonebridge.model.CommandHistoryEntry
import com.quyetbkhoa.phonebridge.model.CommandResult
import com.quyetbkhoa.phonebridge.model.SavedCommand
import com.quyetbkhoa.phonebridge.update.GitHubRelease
import com.quyetbkhoa.phonebridge.update.GitHubUpdateChecker
import com.quyetbkhoa.phonebridge.update.UpdateState
import java.text.DateFormat
import java.util.Date

private enum class Screen(val route: String, val label: String) {
    Home("home", "Home"),
    Terminal("terminal", "Terminal"),
    Presets("presets", "Presets"),
    History("history", "History"),
    Settings("settings", "Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneBridgeRoot(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PhoneBridge") }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = backStack?.destination?.route == screen.route,
                        onClick = { navController.navigate(screen.route) { launchSingleTop = true } },
                        icon = { Text(screen.label.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel) }
            composable(Screen.Terminal.route) { TerminalScreen(viewModel) }
            composable(Screen.Presets.route) { PresetsScreen(viewModel) }
            composable(Screen.History.route) { HistoryScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.connectionState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val result by viewModel.lastResult.collectAsState()
    val running by viewModel.running.collectAsState()
    val connected = state is AdbConnectionState.Connected

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusCard(state)
        }
        if (state is AdbConnectionState.Connected) {
            val remote = (state as AdbConnectionState.Connected).device
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Remote", style = MaterialTheme.typography.titleMedium)
                        Text("${remote.manufacturer} ${remote.model}", style = MaterialTheme.typography.headlineSmall)
                        Text("Android ${remote.androidVersion} · SDK ${remote.sdk}")
                        Text(remote.fingerprint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::connect, enabled = !connected && !running) { Text("CONNECT") }
                OutlinedButton(onClick = viewModel::disconnect, enabled = connected) { Text("DISCONNECT") }
            }
        }
        item {
            Button(
                onClick = viewModel::startShizuku,
                enabled = connected && !running,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (running) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                else Text("START SHIZUKU")
            }
        }
        if (presets.isNotEmpty()) {
            item { Text("Recent presets", style = MaterialTheme.typography.titleMedium) }
            items(presets.take(3), key = { it.id }) { preset ->
                OutlinedButton(
                    onClick = { viewModel.runCommand(preset.command) },
                    enabled = connected && !running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(preset.name) }
            }
        }
        result?.let { value -> item { CommandOutput(value, running) } }
    }
}

@Composable
private fun StatusCard(state: AdbConnectionState) {
    val (usb, adb, detail) = when (state) {
        AdbConnectionState.Disconnected -> Triple("Not detected", "Disconnected", "Connect both phones with a USB-C data cable.")
        AdbConnectionState.UsbDetected -> Triple("Detected", "Disconnected", "ADB USB interface found.")
        AdbConnectionState.PermissionRequired -> Triple("Detected", "Permission required", "Approve the Android USB permission dialog.")
        AdbConnectionState.Connecting -> Triple("Detected", "Connecting", "Opening the USB bulk transport.")
        AdbConnectionState.Authenticating -> Triple("Connected", "Authenticating", "Signing the adbd authentication token.")
        AdbConnectionState.WaitingForAuthorization -> Triple("Connected", "Waiting for authorization", "Accept the RSA dialog on the remote phone and select Always allow.")
        is AdbConnectionState.Connected -> Triple("Connected", "Authorized", "Ready to run remote commands.")
        is AdbConnectionState.Error -> Triple("Unknown", "Error", state.message)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            KeyValue("USB", usb)
            KeyValue("ADB", adb)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value)
    }
}

@Composable
private fun TerminalScreen(viewModel: MainViewModel) {
    val command by viewModel.terminalText.collectAsState()
    val result by viewModel.lastResult.collectAsState()
    val running by viewModel.running.collectAsState()
    val longRunning by viewModel.longRunning.collectAsState()
    var saveMode by remember { mutableStateOf<SaveMode?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importScript)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = command,
                onValueChange = viewModel::setTerminalText,
                label = { Text("Remote shell command or script") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::runTerminal, enabled = command.isNotBlank() && !running) { Text("RUN") }
                OutlinedButton(onClick = { saveMode = SaveMode.Save }, enabled = command.isNotBlank()) { Text("SAVE") }
                OutlinedButton(onClick = viewModel::clearTerminal) { Text("CLEAR") }
                OutlinedButton(onClick = viewModel::stopCommand, enabled = running) { Text("STOP") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/x-sh", "application/octet-stream")) }) {
                    Text("IMPORT .SH")
                }
                OutlinedButton(onClick = { saveMode = SaveMode.RunAndSave }, enabled = command.isNotBlank() && !running) {
                    Text("RUN & SAVE")
                }
            }
        }
        if (running) {
            item { CircularProgressIndicator() }
            if (longRunning) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "The remote command is still running. Long-lived scripts may never exit on their own. Output is shown below as it arrives; tap STOP to close the ADB connection.",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
        result?.let { value -> item { CommandOutput(value, running) } }
    }
    saveMode?.let { mode ->
        NameDialog(
            title = if (mode == SaveMode.Save) "Save preset" else "Run and save",
            onDismiss = { saveMode = null },
            onConfirm = { name ->
                if (mode == SaveMode.Save) viewModel.saveTerminalAsPreset(name) else viewModel.runAndSaveScript(name)
                saveMode = null
            }
        )
    }
}

private enum class SaveMode { Save, RunAndSave }

@Composable
private fun CommandOutput(result: CommandResult, running: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Result", style = MaterialTheme.typography.titleMedium)
            Text(
                if (running) "Running · ${result.durationMs} ms"
                else "Exit: ${result.exitCode?.toString() ?: "unavailable"} · ${result.durationMs} ms"
            )
            if (result.stdout.isNotEmpty()) {
                Text("stdout", fontWeight = FontWeight.Bold)
                Text(result.stdout, fontFamily = FontFamily.Monospace)
            }
            if (result.stderr.isNotEmpty()) {
                Text("stderr", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(result.stderr, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PresetsScreen(viewModel: MainViewModel) {
    val presets by viewModel.presets.collectAsState()
    val running by viewModel.running.collectAsState()
    var editing by remember { mutableStateOf<SavedCommand?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("CREATE PRESET") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { preset ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text(preset.command, maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.runCommand(preset.command) }, enabled = !running) { Text("RUN") }
                            TextButton(onClick = { editing = preset }) { Text("EDIT") }
                            TextButton(onClick = { viewModel.duplicatePreset(preset.id) }) { Text("DUPLICATE") }
                            TextButton(onClick = { viewModel.movePreset(preset.id, -1) }) { Text("↑") }
                            TextButton(onClick = { viewModel.movePreset(preset.id, 1) }) { Text("↓") }
                            if (!preset.isBuiltIn) {
                                TextButton(onClick = { viewModel.deletePreset(preset.id) }) { Text("DELETE") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        PresetDialog(null, onDismiss = { creating = false }) { name, command ->
            viewModel.savePreset(name, command)
            creating = false
        }
    }
    editing?.let { preset ->
        PresetDialog(preset, onDismiss = { editing = null }) { name, command ->
            viewModel.savePreset(name, command, preset.id)
            editing = null
        }
    }
}

@Composable
private fun PresetDialog(preset: SavedCommand?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(preset?.id) { mutableStateOf(preset?.name.orEmpty()) }
    var command by remember(preset?.id) { mutableStateOf(preset?.command.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset == null) "Create preset" else "Edit preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(command, { command = it }, label = { Text("Command") }, minLines = 5)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, command) }, enabled = name.isNotBlank() && command.isNotBlank()) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Preset name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun HistoryScreen(viewModel: MainViewModel) {
    val history by viewModel.history.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Command history", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = viewModel::clearHistory) { Text("CLEAR") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.id }) { item -> HistoryItem(item, onRun = { viewModel.runCommand(item.command) }) }
        }
    }
}

@Composable
private fun HistoryItem(item: CommandHistoryEntry, onRun: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(DateFormat.getDateTimeInstance().format(Date(item.executedAt)), style = MaterialTheme.typography.labelMedium)
            Text(item.command, maxLines = 4, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            Text("Exit: ${item.exitCode?.toString() ?: "unavailable"} · ${item.durationMs} ms")
            if (item.stdout.isNotBlank()) Text(item.stdout, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (item.stderr.isNotBlank()) Text(item.stderr, color = MaterialTheme.colorScheme.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onRun) { Text("RUN AGAIN") }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    var presetMenu by remember { mutableStateOf(false) }
    var changelog by remember { mutableStateOf<GitHubRelease?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Automation", style = MaterialTheme.typography.titleLarge) }
        item { SwitchRow("Auto connect trusted device", settings.autoConnect, viewModel::setAutoConnect) }
        item { SwitchRow("Auto start Shizuku", settings.autoStartShizuku, viewModel::setAutoStartShizuku) }
        item {
            Box {
                OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-run preset: ${presets.firstOrNull { it.id == settings.autoRunPresetId }?.name ?: "Off"}")
                }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                    DropdownMenuItem(text = { Text("Off") }, onClick = { viewModel.setAutoRunPreset(null); presetMenu = false })
                    presets.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.name) }, onClick = { viewModel.setAutoRunPreset(preset.id); presetMenu = false })
                    }
                }
            }
        }
        item { HorizontalDivider() }
        item { Text("App Update", style = MaterialTheme.typography.titleLarge) }
        item { Text("Current version: ${BuildConfig.VERSION_NAME}\nBuild: ${BuildConfig.VERSION_CODE}") }
        item {
            Button(onClick = viewModel::checkUpdate) { Text("CHECK UPDATE") }
        }
        item {
            when (val state = updateState) {
                UpdateState.Idle -> Text("Updates are downloaded only from this project's GitHub Releases.")
                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.height(20.dp).width(20.dp)); Spacer(Modifier.width(8.dp)); Text("Checking…") }
                is UpdateState.UpToDate -> Text("Up to date (${state.version})")
                is UpdateState.Available -> Column {
                    Text("${state.release.name ?: state.release.tagName} available")
                    Row {
                        TextButton(onClick = { changelog = state.release }) { Text("VIEW CHANGELOG") }
                        Button(onClick = { viewModel.installUpdate(state.release) }) { Text("UPDATE") }
                    }
                }
                is UpdateState.Downloading -> Text("Downloading… ${state.progressPercent?.let { "$it%" } ?: ""}")
                is UpdateState.InstallPermissionRequired -> Column {
                    Text("Allow installs from PhoneBridge, return here, then continue installation.")
                    Button(onClick = { viewModel.installVerified(state.apk) }) { Text("CONTINUE INSTALL") }
                }
                is UpdateState.ReadyToInstall -> Text("APK verified and ready to install.")
                is UpdateState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        item { HorizontalDivider() }
        item { Text("Developer logs", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("PhoneBridge logs", viewModel.copyLogs()))
                }) { Text("COPY LOGS") }
                OutlinedButton(onClick = viewModel::clearLogs) { Text("CLEAR") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    logs.takeLast(100).joinToString("\n").ifBlank { "No logs yet" },
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item { HorizontalDivider() }
        item { Text("About", style = MaterialTheme.typography.titleLarge) }
        item { Text("PhoneBridge\nAndroid-to-Android ADB over USB Host") }
        item {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, GitHubUpdateChecker.REPOSITORY_URL.toUri()))
            }) { Text("GITHUB") }
        }
    }
    changelog?.let { release ->
        AlertDialog(
            onDismissRequest = { changelog = null },
            title = { Text(release.name ?: release.tagName) },
            text = { Text(release.body.orEmpty().ifBlank { "No release notes." }) },
            confirmButton = { TextButton(onClick = { changelog = null }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
