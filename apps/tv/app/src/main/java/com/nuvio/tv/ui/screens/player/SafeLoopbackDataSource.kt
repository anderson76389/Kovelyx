package com.nuvio.tv.ui.screens.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Contourne uniquement la route loopback cassée des AAR Media3 :
 * OkHttp conserve le vrai statut HTTP (404, 416, etc.). Tous les autres schémas
 * utilisent le DefaultDataSource d'origine (file, content, asset, etc.).
 */
@UnstableApi
internal class SafeLoopbackDataSource(
    private val http: DataSource,
    private val normal: DataSource
) : DataSource {
    private var active: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "Source déjà ouverte" }
        val target = if (TvMedia3SafetyPolicy.useSafeLoopbackHttp(dataSpec.uri.scheme, dataSpec.uri.host)) {
            http
        } else normal
        active = target
        return try {
            target.open(dataSpec)
        } catch (failure: Throwable) {
            active = null
            try { target.close() } catch (_: Exception) { /* l'erreur d'ouverture est prioritaire */ }
            throw failure
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "Source non ouverte" }.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) {
        http.addTransferListener(transferListener)
        normal.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        val current = active
        active = null
        current?.close()
    }
}
