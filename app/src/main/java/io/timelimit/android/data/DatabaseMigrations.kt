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

    val MIGRATE_TO_V5 = object: Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // device table
            database.execSQL("ALTER TABLE `device` ADD COLUMN `current_overlay_permission` TEXT NOT NULL DEFAULT \"not granted\"")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `highest_overlay_permission` TEXT NOT NULL DEFAULT \"not granted\"")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `current_accessibility_service_permission` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `was_accessibility_service_permission` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `enable_activity_level_blocking` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `q_or_later` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `default_user` TEXT NOT NULL DEFAULT \"\"")
            database.execSQL("ALTER TABLE `device` ADD COLUMN `default_user_timeout` INTEGER NOT NULL DEFAULT 0")

            // category table
            database.execSQL("ALTER TABLE `category` ADD COLUMN `block_all_notifications` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `category` ADD COLUMN `time_warnings` INTEGER NOT NULL DEFAULT 0")

            // app_activity table
            database.execSQL("CREATE TABLE IF NOT EXISTS `app_activity` (`device_id` TEXT NOT NULL, `app_package_name` TEXT NOT NULL, `activity_class_name` TEXT NOT NULL, `activity_title` TEXT NOT NULL, PRIMARY KEY(`device_id`, `app_package_name`, `activity_class_name`))")

            // allowed_contact table
            database.execSQL("CREATE TABLE IF NOT EXISTS `allowed_contact` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `phone` TEXT NOT NULL)")
        }
    }

    val MIGRATE_TO_V6 = object: Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `device` ADD COLUMN `had_manipulation_flags` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V7 = object: Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `user` ADD COLUMN `blocked_times` TEXT NOT NULL DEFAULT \"\"")
        }
    }

    val MIGRATE_TO_V8 = object: Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `category` ADD COLUMN `min_battery_charging` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `category` ADD COLUMN `min_battery_mobile` INTEGER NOT NULL DEFAULT 0")
        }
    }
}