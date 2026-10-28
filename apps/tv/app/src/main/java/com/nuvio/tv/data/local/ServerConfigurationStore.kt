package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.ServerCapabilities
import com.nuvio.tv.domain.model.ServerConfiguration
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
        if (backendUrl.isBlank() || publishableKey.isBlank() || (!emailPasswordAuth && !tvLogin)) {
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
            discoveryUrl = preferences.getString(KEY_DISCOVERY_URL, null),
            tvLoginWebBaseUrl = "$backendUrl/tv-login",
            deviceLoginWebBaseUrl = "$backendUrl/link",
            avatarPublicBaseUrl = "$backendUrl/storage/v1/object/public/avatars"
        )
    }

    fun saveCustom(configuration: ServerConfiguration): Boolean {
        if (!BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED) return false
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
        // Une clé Supabase n'est ni nécessaire ni compatible avec Better Auth.
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
