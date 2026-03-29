package com.musicali.app.di

import com.musicali.app.auth.AuthInterceptor
import com.musicali.app.data.local.AppDatabase
import com.musicali.app.data.remote.ScrapingRepository
import com.musicali.app.data.remote.ScrapingRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Bare client for unauthenticated requests (EveryNoise scraping). */
    @Provides
    @Singleton
    @Named("scraping")
    fun provideScrapingOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Auth client for YouTube API requests — adds Bearer token via AuthInterceptor. */
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideScrapingRepository(
        @Named("scraping") client: OkHttpClient,
        database: AppDatabase
    ): ScrapingRepository = ScrapingRepositoryImpl(client, database)
}
