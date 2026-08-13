package com.quyetbkhoa.phonebridge.update

import com.quyetbkhoa.phonebridge.util.SemanticVersion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
) {
    val version: SemanticVersion? get() = SemanticVersion.parse(tagName)
    val apk: GitHubAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    val checksum: GitHubAsset? get() = assets.firstOrNull { it.name.endsWith(".apk.sha256", ignoreCase = true) }
}

object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(value: String): GitHubRelease = json.decodeFromString(value)
}
