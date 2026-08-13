package com.quyetbkhoa.phonebridge.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.quyetbkhoa.phonebridge.BuildConfig
import com.quyetbkhoa.phonebridge.util.Sha256
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data class Downloading(val progressPercent: Int?) : UpdateState
    data class InstallPermissionRequired(val apk: File) : UpdateState
    data class ReadyToInstall(val apk: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class AppUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val checker: GitHubUpdateChecker = GitHubUpdateChecker()
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    fun check() {
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { checker.check() }.fold(
                onSuccess = { result ->
                    if (result.updateAvailable) UpdateState.Available(result.release)
                    else UpdateState.UpToDate(result.release.tagName)
                },
                onFailure = { UpdateState.Error(it.message ?: "Update check failed") }
            )
        }
    }

    fun downloadAndInstall(release: GitHubRelease) {
        job?.cancel()
        job = scope.launch {
            try {
                val apkAsset = release.apk ?: throw IOException("Release has no APK asset")
                val checksumAsset = release.checksum ?: throw IOException("Release has no SHA-256 asset")
                require(GitHubUpdateChecker.isConfiguredReleaseAsset(apkAsset.downloadUrl)) { "Unexpected APK source" }
                require(GitHubUpdateChecker.isConfiguredReleaseAsset(checksumAsset.downloadUrl)) { "Unexpected checksum source" }
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(directory, apkAsset.name)
                withContext(Dispatchers.IO) {
                    download(apkAsset.downloadUrl, apk) { progress -> _state.value = UpdateState.Downloading(progress) }
                    val expected = downloadText(checksumAsset.downloadUrl).trim().split(Regex("\\s+"), limit = 2).firstOrNull()
                        ?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                        ?: throw IOException("Invalid SHA-256 file")
                    val actual = Sha256.of(apk)
                    if (actual != expected) {
                        apk.delete()
                        throw IOException("SHA-256 mismatch; downloaded APK was deleted")
                    }
                }
                if (!canInstallPackages()) {
                    _state.value = UpdateState.InstallPermissionRequired(apk)
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    _state.value = UpdateState.ReadyToInstall(apk)
                    openInstaller(apk)
                }
            } catch (error: Throwable) {
                _state.value = UpdateState.Error(error.message ?: "Update failed")
            }
        }
    }

    fun installVerified(apk: File) {
        when {
            !apk.exists() -> _state.value = UpdateState.Error("Downloaded APK no longer exists")
            !canInstallPackages() -> context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            else -> openInstaller(apk)
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun openInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun download(url: String, destination: File, onProgress: (Int?) -> Unit) {
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        val connection = GitHubUpdateChecker.openGitHubConnection(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("Download failed: HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var downloaded = 0L
            connection.inputStream.buffered().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(total?.let { ((downloaded * 100) / it).toInt().coerceIn(0, 100) })
                    }
                }
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Unable to finalize APK download" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun downloadText(url: String): String {
        val connection: HttpURLConnection = GitHubUpdateChecker.openGitHubConnection(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("Checksum download failed: HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText().take(MAX_CHECKSUM_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CURRENT_VERSION = BuildConfig.VERSION_NAME
        private const val MAX_CHECKSUM_BYTES = 4096
    }
}
