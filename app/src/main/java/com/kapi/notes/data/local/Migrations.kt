package com.kapi.notes.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change up to version 9 relied on Room's [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration],
 * which just wipes and recreates the local database - acceptable while the app had no real users,
 * but no longer: a wipe now means losing everyone's local notes (only what's synced to R2 survives).
 * This is the project's first real [Migration] - add new ones here (and register them in
 * AppModule.provideDatabase) instead of bumping the version and letting the destructive fallback
 * handle it, so future schema changes don't cost users their data either.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pages ADD COLUMN recognizedText TEXT")
        db.execSQL("ALTER TABLE pages ADD COLUMN recognizedTextUpdatedAt INTEGER")
    }
}
