package com.kovelyx.tv.core.di

import com.kovelyx.tv.data.simkl.AndroidSimklAuthStorage
import com.kovelyx.tv.data.simkl.AndroidSimklSyncStorage
import com.kovelyx.tv.data.simkl.SimklAuthStorage
import com.kovelyx.tv.data.simkl.SimklApiSyncRemote
import com.kovelyx.tv.data.simkl.SimklSyncRemote
import com.kovelyx.tv.data.simkl.SimklSyncStorage
import com.kovelyx.tv.core.tracking.TrackingLibraryProvider
import com.kovelyx.tv.core.tracking.TrackingProvider
import com.kovelyx.tv.data.repository.TraktTrackingLibraryProvider
import com.kovelyx.tv.data.repository.TraktTrackingProvider
import com.kovelyx.tv.data.simkl.SimklLibraryService
import com.kovelyx.tv.core.tracking.TrackingHistoryWriter
import com.kovelyx.tv.core.tracking.TrackingProgressProvider
import com.kovelyx.tv.data.repository.TraktTrackingHistoryWriter
import com.kovelyx.tv.data.repository.TraktTrackingProgressProvider
import com.kovelyx.tv.data.simkl.SimklTrackingHistoryWriter
import com.kovelyx.tv.data.simkl.SimklTrackingProgressProvider
import com.kovelyx.tv.data.simkl.SimklTrackingProvider
import com.kovelyx.tv.core.profile.ProfileScopedCredentialStore
import com.kovelyx.tv.data.mdblist.MdbListTrackingHistoryWriter
import com.kovelyx.tv.data.mdblist.MdbListTrackingProgressProvider
import com.kovelyx.tv.data.mdblist.MdbListTrackingProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @IntoSet
    abstract fun bindMdbListLibrary(provider: com.kovelyx.tv.data.mdblist.MdbListTrackingLibraryProvider): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindMdbListProvider(provider: MdbListTrackingProvider): TrackingProvider

    @Binds
    @IntoSet
    abstract fun bindMdbListProgress(provider: MdbListTrackingProgressProvider): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindMdbListHistory(writer: MdbListTrackingHistoryWriter): TrackingHistoryWriter

    @Binds
    @Singleton
    abstract fun bindSimklAuthStorage(storage: AndroidSimklAuthStorage): SimklAuthStorage

    @Binds
    @IntoSet
    abstract fun bindSimklProfileScopedCredentialStore(
        storage: AndroidSimklAuthStorage
    ): ProfileScopedCredentialStore

    @Binds
    @Singleton
    abstract fun bindSimklSyncStorage(storage: AndroidSimklSyncStorage): SimklSyncStorage

    @Binds
    @Singleton
    abstract fun bindSimklSyncRemote(remote: SimklApiSyncRemote): SimklSyncRemote

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingLibraryProvider(
        provider: TraktTrackingLibraryProvider
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingLibraryProvider(
        provider: SimklLibraryService
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProgressProvider(
        provider: TraktTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProgressProvider(
        provider: SimklTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingHistoryWriter(
        writer: TraktTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingHistoryWriter(
        writer: SimklTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProvider(
        provider: TraktTrackingProvider
    ): TrackingProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProvider(
        provider: SimklTrackingProvider
    ): TrackingProvider
}
