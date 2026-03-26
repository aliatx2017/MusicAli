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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
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
        client: OkHttpClient,
        database: AppDatabase
    ): ScrapingRepository = ScrapingRepositoryImpl(client, database)
}
