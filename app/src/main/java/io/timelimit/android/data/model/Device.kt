/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.integration.platform.*

@Entity(tableName = "device")
@TypeConverters(
        ProtectionLevelConverter::class,
        RuntimePermissionStatusConverter::class,
        NewPermissionStatusConverter::class
)
data class Device(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "name")
        val name: String,
        @ColumnInfo(name = "model")
        val model: String,
        @ColumnInfo(name = "added_at")
        val addedAt: Long,
        @ColumnInfo(name = "current_user_id")
        val currentUserId: String,      // empty if not set
        @ColumnInfo(name = "current_protection_level")
        val currentProtectionLevel: ProtectionLevel,
        @ColumnInfo(name = "highest_permission_level")
        val highestProtectionLevel: ProtectionLevel,
        @ColumnInfo(name = "current_usage_stats_permission")
        val currentUsageStatsPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "highest_usage_stats_permission")
        val highestUsageStatsPermission: RuntimePermissionStatus,
        @ColumnInfo(name = "current_notification_access_permission")
        val currentNotificationAccessPermission: NewPermissionStatus,
        @ColumnInfo(name = "highest_notification_access_permission")
        val highestNotificationAccessPermission: NewPermissionStatus,
        @ColumnInfo(name = "current_app_version")
        val currentAppVersion: Int,
        @ColumnInfo(name = "highest_app_version")
        val highestAppVersion: Int,
        @ColumnInfo(name = "tried_disabling_device_admin")
        val manipulationTriedDisablingDeviceAdmin: Boolean,
        @ColumnInfo(name = "had_manipulation")
        val hadManipulation: Boolean
): JsonSerializable {
    companion object {
        private const val ID = "id"
        private const val NAME = "n"
        private const val MODEL = "m"
        private const val ADDED_AT = "aa"
        private const val CURRENT_USER_ID = "u"
        private const val CURRENT_PROTECTION_LEVEL = "pc"
        private const val HIGHEST_PROTECTION_LEVEL = "pm"
        private const val CURRENT_USAGE_STATS_PERMISSION = "uc"
        private const val HIGHEST_USAGE_STATS_PERMISSION = "um"
        private const val CURRENT_NOTIFICATION_ACCESS_PERMISSION = "nc"
        private const val HIGHEST_NOTIFICATION_ACCESS_PERMISSION = "nm"
        private const val CURRENT_APP_VERSION = "ac"
        private const val HIGHEST_APP_VERSION = "am"
        private const val TRIED_DISABLING_DEVICE_ADMIN = "tdda"
        private const val HAD_MANIPULATION = "hm"

        fun parse(reader: JsonReader): Device {
            var id: String? = null
            var name: String? = null
            var model: String? = null
            var addedAt: Long? = null
            var currentUserId: String? = null
            var currentProtectionLevel: ProtectionLevel? = null
            var highestProtectionLevel: ProtectionLevel? = null
            var currentUsageStatsPermission: RuntimePermissionStatus? = null
            var highestUsageStatsPermission: RuntimePermissionStatus? = null
            var currentNotificationAccessPermission: NewPermissionStatus? = null
            var highestNotificationAccessPermission: NewPermissionStatus? = null
            var currentAppVersion: Int? = null
            var highestAppVersion: Int? = null
            var manipulationTriedDisablingDeviceAdmin: Boolean? = null
            var hadManipulation: Boolean? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    NAME -> name = reader.nextString()
                    MODEL -> model = reader.nextString()
                    ADDED_AT -> addedAt = reader.nextLong()
                    CURRENT_USER_ID -> currentUserId = reader.nextString()
                    CURRENT_PROTECTION_LEVEL -> currentProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    HIGHEST_PROTECTION_LEVEL -> highestProtectionLevel = ProtectionLevelUtil.parse(reader.nextString())
                    CURRENT_USAGE_STATS_PERMISSION -> currentUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_USAGE_STATS_PERMISSION -> highestUsageStatsPermission = RuntimePermissionStatusUtil.parse(reader.nextString())
                    CURRENT_NOTIFICATION_ACCESS_PERMISSION -> currentNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    HIGHEST_NOTIFICATION_ACCESS_PERMISSION -> highestNotificationAccessPermission = NewPermissionStatusUtil.parse(reader.nextString())
                    CURRENT_APP_VERSION -> currentAppVersion = reader.nextInt()
                    HIGHEST_APP_VERSION -> highestAppVersion = reader.nextInt()
                    TRIED_DISABLING_DEVICE_ADMIN -> manipulationTriedDisablingDeviceAdmin = reader.nextBoolean()
                    HAD_MANIPULATION -> hadManipulation = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return Device(
                    id = id!!,
                    name = name!!,
                    model = model!!,
                    addedAt = addedAt!!,
                    currentUserId = currentUserId!!,
                    currentProtectionLevel = currentProtectionLevel!!,
                    highestProtectionLevel = highestProtectionLevel!!,
                    currentUsageStatsPermission = currentUsageStatsPermission!!,
                    highestUsageStatsPermission = highestUsageStatsPermission!!,
                    currentNotificationAccessPermission = currentNotificationAccessPermission!!,
                    highestNotificationAccessPermission = highestNotificationAccessPermission!!,
                    currentAppVersion = currentAppVersion!!,
                    highestAppVersion = highestAppVersion!!,
                    manipulationTriedDisablingDeviceAdmin = manipulationTriedDisablingDeviceAdmin!!,
                    hadManipulation = hadManipulation!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)

        if (currentUserId.isNotEmpty()) {
            IdGenerator.assertIdValid(currentUserId)
        }

        if (name.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (model.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (addedAt < 0) {
            throw IllegalArgumentException()
        }

        if (currentAppVersion < 0 || highestAppVersion < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(NAME).value(name)
        writer.name(MODEL).value(model)
        writer.name(ADDED_AT).value(addedAt)
        writer.name(CURRENT_USER_ID).value(currentUserId)
        writer.name(CURRENT_PROTECTION_LEVEL).value(ProtectionLevelUtil.serialize(currentProtectionLevel))
        writer.name(HIGHEST_PROTECTION_LEVEL).value(ProtectionLevelUtil.serialize(highestProtectionLevel))
        writer.name(CURRENT_USAGE_STATS_PERMISSION).value(RuntimePermissionStatusUtil.serialize(currentUsageStatsPermission))
        writer.name(HIGHEST_USAGE_STATS_PERMISSION).value(RuntimePermissionStatusUtil.serialize(highestUsageStatsPermission))
        writer.name(CURRENT_NOTIFICATION_ACCESS_PERMISSION).value(NewPermissionStatusUtil.serialize(currentNotificationAccessPermission))
        writer.name(HIGHEST_NOTIFICATION_ACCESS_PERMISSION).value(NewPermissionStatusUtil.serialize(highestNotificationAccessPermission))
        writer.name(CURRENT_APP_VERSION).value(currentAppVersion)
        writer.name(HIGHEST_APP_VERSION).value(highestAppVersion)
        writer.name(TRIED_DISABLING_DEVICE_ADMIN).value(manipulationTriedDisablingDeviceAdmin)
        writer.name(HAD_MANIPULATION).value(hadManipulation)

        writer.endObject()
    }

    @Transient
    val manipulationOfProtectionLevel = currentProtectionLevel != highestProtectionLevel
    @Transient
    val manipulationOfUsageStats = currentUsageStatsPermission != highestUsageStatsPermission
    @Transient
    val manipulationOfNotificationAccess = currentNotificationAccessPermission != highestNotificationAccessPermission
    @Transient
    val manipulationOfAppVersion = currentAppVersion != highestAppVersion

    @Transient
    val hasActiveManipulationWarning = manipulationOfProtectionLevel ||
            manipulationOfUsageStats ||
            manipulationOfNotificationAccess ||
            manipulationOfAppVersion ||
            manipulationTriedDisablingDeviceAdmin

    @Transient
    val hasAnyManipulation = hasActiveManipulationWarning || hadManipulation
}
