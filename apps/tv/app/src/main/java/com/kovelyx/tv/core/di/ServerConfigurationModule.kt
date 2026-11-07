package com.kovelyx.tv.core.di

import com.kovelyx.tv.data.local.ServerConfigurationStore
import com.kovelyx.tv.domain.model.ServerConfiguration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Local server configuration only; account sessions are owned by Kovelyx Cloudflare. */
@Module
@InstallIn(SingletonComponent::class)
object ServerConfigurationModule {
    @Provides
    @Singleton
    fun provideActiveServerConfiguration(
        configurationStore: ServerConfigurationStore
    ): ServerConfiguration = configurationStore.loadActive()
}
