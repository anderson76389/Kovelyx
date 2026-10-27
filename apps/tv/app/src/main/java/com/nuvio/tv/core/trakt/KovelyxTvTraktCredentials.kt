package com.nuvio.tv.core.trakt

import android.content.Context
import com.nuvio.tv.BuildConfig

/**
 * Identifiants OAuth personnels de cet appareil TV.
 * L'utilisateur les définit dans Paramètres > Suivi > Trakt.
 * Aucune clé personnelle n'est intégrée à l'APK ou envoyée à GitHub.
 */
internal object KovelyxTvTraktCredentials {
    private const val FILE = "kovelyx_tv_trakt_credentials"
    private const val CLIENT_ID = "client_id"
    private const val CLIENT_SECRET = "client_secret"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun savedClientId(context: Context): String =
        preferences(context).getString(CLIENT_ID, "").orEmpty()

    fun savedClientSecret(context: Context): String =
        preferences(context).getString(CLIENT_SECRET, "").orEmpty()

    fun clientId(context: Context): String =
        savedClientId(context).ifBlank { BuildConfig.TRAKT_CLIENT_ID }

    fun clientSecret(context: Context): String =
        savedClientSecret(context).ifBlank { BuildConfig.TRAKT_CLIENT_SECRET }

    fun save(context: Context, clientId: String, clientSecret: String) {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        require((id.isBlank() && secret.isBlank()) || (id.isNotBlank() && secret.isNotBlank())) {
            "Renseignez l'identifiant Trakt et son secret ensemble."
        }
        check(
            preferences(context).edit()
                .putString(CLIENT_ID, id)
                .putString(CLIENT_SECRET, secret)
                .commit()
        ) { "Impossible d'enregistrer les clés Trakt sur cette télévision." }
    }
}
