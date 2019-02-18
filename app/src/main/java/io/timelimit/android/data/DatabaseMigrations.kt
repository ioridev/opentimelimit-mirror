package io.timelimit.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATE_TO_V2 = object: Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `user` ADD COLUMN `category_for_not_assigned_apps` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V3 = object: Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `category` ADD COLUMN `parent_category_id` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V4 = object: Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `device` ADD COLUMN `did_reboot` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `consider_reboot_manipulation` INTEGER NOT NULL DEFAULT 0")
        }
    }
}