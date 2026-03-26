package com.musicali.app.di

import android.content.Context
import androidx.room.Room
import com.musicali.app.data.local.AppDatabase
import com.musicali.app.data.local.ArtistDao
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
            .build()

    @Provides
    fun provideArtistDao(db: AppDatabase): ArtistDao = db.artistDao()
}
