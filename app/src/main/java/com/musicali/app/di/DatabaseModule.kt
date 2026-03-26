package com.musicali.app.di

import android.content.Context
import androidx.room.Room
import com.musicali.app.data.local.AppDatabase
import com.musicali.app.data.local.ArtistDao
import com.musicali.app.data.local.GenreCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musicali.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideArtistDao(db: AppDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideGenreCacheDao(db: AppDatabase): GenreCacheDao = db.genreCacheDao()
}
