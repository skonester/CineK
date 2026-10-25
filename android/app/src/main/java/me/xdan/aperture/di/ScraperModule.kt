package me.xdan.aperture.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.xdan.aperture.data.sources.OkHttpScraperClient
import me.xdan.aperture.data.sources.StreamingScrapers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {
    @Provides
    @Singleton
    fun provideStreamingScrapers(): StreamingScrapers {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        return StreamingScrapers(OkHttpScraperClient(client))
    }
}
