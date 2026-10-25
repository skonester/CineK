package me.xdan.aperture.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.xdan.aperture.data.remoteplayback.RemoteSourceLookup
import me.xdan.aperture.data.remoteplayback.ScraperRemoteSourceLookup

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteSourceModule {
    @Binds abstract fun bindRemoteSourceLookup(lookup: ScraperRemoteSourceLookup): RemoteSourceLookup
}
