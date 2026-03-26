package com.musicali.app.di

import android.content.Context
import com.musicali.app.auth.AuthRepository
import com.musicali.app.auth.AuthRepositoryImpl
import com.musicali.app.auth.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {
        @Provides
        @Singleton
        fun provideTokenStore(@ApplicationContext context: Context): TokenStore =
            TokenStore.create(context)
    }
}
