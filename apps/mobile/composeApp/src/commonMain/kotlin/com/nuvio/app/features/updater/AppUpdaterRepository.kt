package com.nuvio.app.features.updater

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.updates_github_api_error
import org.jetbrains.compose.resources.getString

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long? = null,
    @SerialName("content_type") val contentType: String? = null,
)

internal class NoChannelReleaseException : IllegalStateException()

/**
 * Debug APKs use com.kovelyx.mobile.debug and an ephemeral CI key;
 * signed releases use com.kovelyx.mobile and a permanent key.
 * Never select a package from the other release family.
 */
internal enum class AndroidApkReleaseFamily { ANY, DEBUG, SIGNED }


internal object AppUpdaterRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getLatestChannelUpdate(channel: UpdateChannel): Result<AppUpdate> = runCatching {
        val response = httpRequestRaw(
            method = "GET",
            url = "https://api.github.com/repos/anderson76389/Kovelyx/${releasePath(channel)}",
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to "Kovelyx-Mobile",
            ),
            body = "",
        )
        currentCoroutineContext().ensureActive()
        if (response.status == 404) throw NoChannelReleaseException()
        if (response.status !in 200..299) {
            error(getString(Res.string.updates_github_api_error, response.status))
        }
        selectUpdate(
            response.body,
            channel,
            AppUpdaterPlatform.getSupportedAbis(),
            family = if (AppUpdaterPlatform.isDebugBuild) {
                AndroidApkReleaseFamily.DEBUG
            } else {
                AndroidApkReleaseFamily.SIGNED
            },
        )
            ?: throw NoChannelReleaseException()
    }

    internal fun releasePath(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "releases?per_page=100"
        UpdateChannel.BETA -> "releases?per_page=100"
    }

    internal fun selectUpdate(
        responseBody: String,
        channel: UpdateChannel,
        supportedAbis: List<String>,
        family: AndroidApkReleaseFamily = AndroidApkReleaseFamily.ANY,
    ): AppUpdate? {
        val releases = json.decodeFromString<List<GitHubReleaseDto>>(responseBody)
        return ReleaseSelector.eligibleReleases(releases, channel)
            .filter { release ->
                val tag = release.tagName.orEmpty()
                when (family) {
                    AndroidApkReleaseFamily.ANY -> true
                    AndroidApkReleaseFamily.DEBUG -> tag.startsWith("v", ignoreCase = true)
                    AndroidApkReleaseFamily.SIGNED -> tag.startsWith("android-v", ignoreCase = true)
                }
            }.firstNotNullOfOrNull { release ->
            val candidates = when (family) {
                AndroidApkReleaseFamily.SIGNED -> release.assets.filter {
                    it.name.startsWith("Kovelyx-Android-android-v", ignoreCase = true)
                }
                AndroidApkReleaseFamily.DEBUG -> release.assets.filter {
                    it.name.startsWith("Kovelyx-Android-v", ignoreCase = true)
                }
                AndroidApkReleaseFamily.ANY -> release.assets
            }
            val asset = chooseBestApkAsset(candidates, supportedAbis) ?: return@firstNotNullOfOrNull null
            val tag = release.tagName?.takeIf { VersionUtils.parse(it) != null }
                ?: release.name.orEmpty()
            AppUpdate(
                tag = tag,
                title = release.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size,
            )
        }
    }

    private fun chooseBestApkAsset(
        assets: List<GitHubAssetDto>,
        supportedAbis: List<String>,
    ): GitHubAssetDto? {
        val apkAssets = assets.filter { asset ->
            (asset.name.startsWith("Kovelyx-Mobile-", ignoreCase = true) ||
                 asset.name.startsWith("Kovelyx-Android-", ignoreCase = true)) &&
                asset.name.endsWith(".apk", ignoreCase = true)
        }
        for (abi in supportedAbis) {
            apkAssets.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apkAssets.firstOrNull { asset ->
            val name = asset.name.lowercase()
            name.contains("universal") || name.contains("all")
        } ?: apkAssets.firstOrNull()
    }
}
