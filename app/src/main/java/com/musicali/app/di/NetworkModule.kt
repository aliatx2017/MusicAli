package com.musicali.app.di

import com.musicali.app.data.local.AppDatabase
import com.musicali.app.data.remote.ScrapingRepository
import com.musicali.app.data.remote.ScrapingRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideScrapingRepository(
        client: OkHttpClient,
        database: AppDatabase
    ): ScrapingRepository = ScrapingRepositoryImpl(client, database)
}
