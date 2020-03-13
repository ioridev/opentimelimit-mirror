/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
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

    val MIGRATE_TO_V9 = object: Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `category` ADD COLUMN `temporarily_blocked_end_time` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V10 = object: Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `category` ADD COLUMN `sort` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATE_TO_V11 = object: Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // this is empty
            //
            // a new possible enum value was added, the version upgrade enables the downgrade mechanism
        }
    }

    val MIGRATE_TO_V12 = object: Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `category` ADD COLUMN `extra_time_day` INTEGER NOT NULL DEFAULT -1")
        }
    }
}