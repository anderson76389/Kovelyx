package com.kovelyx.tv.domain.repository

import com.kovelyx.tv.core.network.NetworkResult
import com.kovelyx.tv.domain.model.CatalogRow
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun getCatalog(
        addonBaseUrl: String,
        addonId: String,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        skip: Int = 0,
        skipStep: Int = 100,
        extraArgs: Map<String, String> = emptyMap(),
        supportsSkip: Boolean = false,
        posterScreen: com.kovelyx.tv.core.poster.CustomPosterScreen = com.kovelyx.tv.core.poster.CustomPosterScreen.HOME
    ): Flow<NetworkResult<CatalogRow>>
}
