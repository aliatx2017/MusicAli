package com.musicali.app.di

import com.musicali.app.data.remote.youtube.YouTubeApiService
import com.musicali.app.data.remote.youtube.YouTubeRepository
import com.musicali.app.data.remote.youtube.YouTubeRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class YouTubeModule {

    @Binds
    @Singleton
    abstract fun bindYouTubeRepository(impl: YouTubeRepositoryImpl): YouTubeRepository

    companion object {

        private val json = Json {
            ignoreUnknownKeys = true    // YouTube API may add fields in new API versions
            coerceInputValues = true    // safe default for optional/nullable fields
        }

        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)   // shared client — AuthInterceptor adds Bearer token
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        @Provides
        @Singleton
        fun provideYouTubeApiService(retrofit: Retrofit): YouTubeApiService =
            retrofit.create(YouTubeApiService::class.java)
    }
}
