package com.kapi.notes.di

import android.content.Context
import androidx.room.Room
import com.kapi.notes.data.local.AppDatabase
import com.kapi.notes.data.local.NoteDao
import com.kapi.notes.data.repository.AuthRepository
import com.kapi.notes.data.repository.BackupRepository
import com.kapi.notes.data.repository.DriveSyncAuthRepository
import com.kapi.notes.data.repository.NotesRepository
import com.kapi.notes.data.repository.NotesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        noteDao: NoteDao,
        db: AppDatabase
    ): BackupRepository = BackupRepository(context, noteDao, db)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DriveSyncAuthRepository): AuthRepository
}
