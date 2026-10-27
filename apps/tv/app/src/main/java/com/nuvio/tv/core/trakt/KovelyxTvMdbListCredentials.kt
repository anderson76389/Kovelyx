package com.nuvio.tv.core.trakt

import android.content.Context
import com.nuvio.tv.BuildConfig

/** Client ID OAuth personnel MDBList ; la clé API des notes est un réglage distinct. */
internal object KovelyxTvMdbListCredentials {
    private const val STORE = "kovelyx_tv_mdblist_credentials"
    private const val CLIENT_ID = "client_id"

    fun savedClientId(context: Context): String =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(CLIENT_ID, "").orEmpty()

    fun clientId(context: Context): String =
        savedClientId(context).ifBlank { BuildConfig.MDBLIST_CLIENT_ID }

    fun save(context: Context, clientId: String) {
        val id = clientId.trim()
        require(id.length <= 256) { "Identifiant MDBList trop long." }
        check(
            context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
                .edit().putString(CLIENT_ID, id).commit()
        ) { "Impossible d'enregistrer le Client ID MDBList sur cette télévision." }
    }
}
