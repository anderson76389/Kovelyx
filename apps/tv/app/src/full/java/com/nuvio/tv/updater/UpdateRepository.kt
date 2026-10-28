package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.updater.model.AppUpdate
import javax.inject.Inject
import javax.inject.Singleton

internal class NoEligibleUpdateException(channel: UpdateChannel) :
    IllegalStateException("Aucune version APK compatible pour le canal ${channel.storedValue}")

/** A permanently signed TV APK cannot replace the separately packaged .debug app. */
internal fun tvReleaseMatchesInstalledVariant(tag: String?, isDebugBuild: Boolean): Boolean =
    if (isDebugBuild) {
        tag?.startsWith("v", ignoreCase = true) == true
    } else {
        tag?.startsWith("tv-v", ignoreCase = true) == true
    }

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    suspend fun getLatestUpdate(channel: UpdateChannel): Result<AppUpdate> {
        return runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO

            val releases = when (channel) {
                UpdateChannel.STABLE -> {
                    // Le "latest" global peut être Windows, Android mobile ou TV.
                    // La sélection doit examiner les assets de chaque plateforme.
                    val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
                    if (!response.isSuccessful) {
                        error("Erreur de l’API GitHub : ${response.code()}")
                    }
                    response.body() ?: error("Réponse des versions GitHub vide")
                }
                UpdateChannel.BETA -> {
                    val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
                    if (!response.isSuccessful) {
                        error("Erreur de l’API GitHub : ${response.code()}")
                    }
                    response.body() ?: error("Réponse des versions GitHub vide")
                }
            }
            val releaseWithAsset = ReleaseSelector
                .eligibleReleases(releases, channel)
                .filter { tvReleaseMatchesInstalledVariant(it.tagName, BuildConfig.IS_DEBUG_BUILD) }
                .firstNotNullOfOrNull { release ->
                    // Une Release Kovelyx peut contenir l'APK mobile ET TV.
                    // N'installer JAMAIS par erreur le fichier Android mobile sur une TV.
                    val tvAssets = release.assets.filter { asset ->
                        asset.name.startsWith("Kovelyx-TV-", ignoreCase = true) &&
                            asset.name.endsWith(".apk", ignoreCase = true)
                    }
                    AbiSelector.chooseBestApkAsset(tvAssets)?.let { asset ->
                        release to asset
                    }
                }
                ?: throw NoEligibleUpdateException(channel)
            val (dto, asset) = releaseWithAsset

            val tag = dto.tagName?.takeIf { it.isNotBlank() }
                ?: dto.name?.takeIf { it.isNotBlank() }
                ?: error("Cette version ne possède ni identifiant ni nom")

            AppUpdate(
                tag = tag,
                title = dto.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = dto.body.orEmpty(),
                releaseUrl = dto.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}
