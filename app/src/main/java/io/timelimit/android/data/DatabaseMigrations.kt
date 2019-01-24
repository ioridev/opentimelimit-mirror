package io.timelimit.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATE_TO_V2 = object: Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `user` ADD COLUMN `category_for_not_assigned_apps` TEXT NOT NULL DEFAULT \"\"")
        }
    }
}