package com.kovelyx.tv.data.local

import android.content.Context
import com.kovelyx.tv.BuildConfig
import com.kovelyx.tv.data.remote.ServerDiscoveryPolicy
import com.kovelyx.tv.domain.model.ServerCapabilities
import com.kovelyx.tv.domain.model.ServerConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConfigurationStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun loadActive(): ServerConfiguration {
        if (!BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED) return officialConfiguration()
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_CUSTOM_ENABLED, false)) return officialConfiguration()
        val backendUrl = preferences.getString(KEY_BACKEND_URL, null)?.trim().orEmpty()
        val publishableKey = preferences.getString(KEY_PUBLISHABLE_KEY, null)?.trim().orEmpty()
        val emailPasswordAuth = preferences.getBoolean(KEY_EMAIL_PASSWORD_AUTH, false)
        val tvLogin = preferences.getBoolean(KEY_TV_LOGIN, false)
        if (backendUrl.isBlank() || publishableKey.isBlank() ||
            ServerDiscoveryPolicy.isRetiredUpstream(backendUrl) ||
            (!emailPasswordAuth && !tvLogin)) {
            return officialConfiguration()
        }
        return ServerConfiguration(
            backendUrl = backendUrl,
            publishableKey = publishableKey,
            capabilities = ServerCapabilities(
                emailPasswordAuth = emailPasswordAuth,
                tvLogin = tvLogin
            ),
            isCustom = true,
            // Preserve stored custom backend credentials while switching discovery to Kovelyx.
            // Existing sessions and local library data must not be cleared.
            discoveryUrl = preferences.getString(KEY_DISCOVERY_URL, null)
                ?.let { saved -> runCatching { ServerDiscoveryPolicy.discoveryUrl(saved) }.getOrNull() },
            tvLoginWebBaseUrl = "$backendUrl/tv-login",
            deviceLoginWebBaseUrl = "$backendUrl/link",
            avatarPublicBaseUrl = "$backendUrl/storage/v1/object/public/avatars"
        )
    }

    fun saveCustom(configuration: ServerConfiguration): Boolean {
        if (!BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED ||
            ServerDiscoveryPolicy.isRetiredUpstream(configuration.backendUrl)) return false
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CUSTOM_ENABLED, true)
            .putString(KEY_BACKEND_URL, configuration.backendUrl)
            .putString(KEY_PUBLISHABLE_KEY, configuration.publishableKey)
            .putBoolean(KEY_EMAIL_PASSWORD_AUTH, configuration.capabilities.emailPasswordAuth)
            .putBoolean(KEY_TV_LOGIN, configuration.capabilities.tvLogin)
            .putString(KEY_DISCOVERY_URL, configuration.discoveryUrl)
            .commit()
    }

    fun useOfficial(): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

    private fun officialConfiguration() = ServerConfiguration(
        // Le serveur officiel Kovelyx est uniquement le Worker Cloudflare.
        // Better Auth n'utilise pas de clé publique de base de données.
        backendUrl = BuildConfig.KOVELYX_CLOUD_URL.trim().trimEnd('/'),
        publishableKey = "",
        capabilities = ServerCapabilities(
            emailPasswordAuth = BuildConfig.KOVELYX_CLOUD_URL.startsWith("https://"),
            // La liaison QR historique via RPC n'existe pas sur ce Worker.
            tvLogin = false
        ),
        isCustom = false,
        fallbackBackendUrl = null,
        tvLoginWebBaseUrl = null,
        deviceLoginWebBaseUrl = null,
        avatarPublicBaseUrl = null
    )

    private companion object {
        const val PREFERENCES_NAME = "server_configuration"
        const val KEY_CUSTOM_ENABLED = "custom_enabled"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_PUBLISHABLE_KEY = "publishable_key"
        const val KEY_EMAIL_PASSWORD_AUTH = "email_password_auth"
        const val KEY_TV_LOGIN = "tv_login"
        const val KEY_DISCOVERY_URL = "discovery_url"
    }
}
