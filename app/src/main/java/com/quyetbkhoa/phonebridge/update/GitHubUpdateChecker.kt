package com.quyetbkhoa.phonebridge.update

import com.quyetbkhoa.phonebridge.BuildConfig
import com.quyetbkhoa.phonebridge.util.SemanticVersion
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdateCheckResult(
    val release: GitHubRelease,
    val updateAvailable: Boolean
)

class GitHubUpdateChecker {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = openGitHubConnection(LATEST_RELEASE_API)
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw IOException("No GitHub Release is available yet")
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}")
            }
            val release = GitHubReleaseParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = release.version ?: throw IOException("Invalid release tag: ${release.tagName}")
            val current = SemanticVersion.parse(BuildConfig.VERSION_NAME.removeSuffix("-debug"))
                ?: throw IOException("Invalid current version: ${BuildConfig.VERSION_NAME}")
            UpdateCheckResult(release, latest > current)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val GITHUB_OWNER = "quyetbkhoa"
        const val GITHUB_REPO = "PhoneBridge"
        const val REPOSITORY_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        fun openGitHubConnection(rawUrl: String): HttpURLConnection {
            var url = URL(rawUrl)
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                require(url.protocol == "https" && url.host.lowercase() in ALLOWED_HOSTS) {
                    "Refusing non-GitHub download host: ${url.host}"
                }
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PhoneBridge/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                if (connection.responseCode !in 300..399) return connection
                val location = connection.getHeaderField("Location") ?: throw IOException("GitHub redirect has no location")
                connection.disconnect()
                if (redirectCount == MAX_REDIRECTS) throw IOException("Too many GitHub redirects")
                url = URL(url, location)
            }
            error("Unreachable")
        }

        fun isConfiguredReleaseAsset(url: String): Boolean =
            url.startsWith("$REPOSITORY_URL/releases/download/")

        private const val MAX_REDIRECTS = 5
        private val ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com"
        )
    }
}
