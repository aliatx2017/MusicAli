package com.musicali.app.di

import com.musicali.app.domain.usecase.GeneratePlaylistUseCase
import com.musicali.app.domain.usecase.PlaylistGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UseCaseModule {
    @Binds
    abstract fun bindPlaylistGenerator(impl: GeneratePlaylistUseCase): PlaylistGenerator
}
