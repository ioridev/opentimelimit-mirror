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
import androidx.room.*
import io.timelimit.android.data.JsonSerializable

@Entity(tableName = "config")
@TypeConverters(ConfigurationItemTypeConverter::class)
data class ConfigurationItem(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val key: ConfigurationItemType,
        @ColumnInfo(name = "value")
        val value: String
): JsonSerializable {
    companion object {
        private const val KEY = "key"
        private const val VALUE = "value"

        // returns null if parsing failed
        fun parse(reader: JsonReader): ConfigurationItem? {
            var key: Int? = null
            var value: String? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    KEY -> key = reader.nextInt()
                    VALUE -> value = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            key!!
            value!!

            try {
                return ConfigurationItem(
                        key = ConfigurationItemTypeUtil.parse(key),
                        value = value
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(KEY).value(ConfigurationItemTypeUtil.serialize(key))
        writer.name(VALUE).value(value)

        writer.endObject()
    }
}

enum class ConfigurationItemType {
    OwnDeviceId,
    ShownHints,
    WasDeviceLocked,
    ForegroundAppQueryRange,
    EnableAlternativeDurationSelection,
    LastScreenOnTime
}

object ConfigurationItemTypeUtil {
    private const val OWN_DEVICE_ID = 1
    private const val SHOWN_HINTS = 2
    private const val WAS_DEVICE_LOCKED = 3
    private const val FOREGROUND_APP_QUERY_RANGE = 4
    private const val ENABLE_ALTERNATIVE_DURATION_SELECTION = 5
    private const val LAST_SCREEN_ON_TIME = 6

    val TYPES = listOf(
            ConfigurationItemType.OwnDeviceId,
            ConfigurationItemType.ShownHints,
            ConfigurationItemType.WasDeviceLocked,
            ConfigurationItemType.ForegroundAppQueryRange,
            ConfigurationItemType.EnableAlternativeDurationSelection,
            ConfigurationItemType.LastScreenOnTime
    )

    fun serialize(value: ConfigurationItemType) = when(value) {
        ConfigurationItemType.OwnDeviceId -> OWN_DEVICE_ID
        ConfigurationItemType.ShownHints -> SHOWN_HINTS
        ConfigurationItemType.WasDeviceLocked -> WAS_DEVICE_LOCKED
        ConfigurationItemType.ForegroundAppQueryRange -> FOREGROUND_APP_QUERY_RANGE
        ConfigurationItemType.EnableAlternativeDurationSelection -> ENABLE_ALTERNATIVE_DURATION_SELECTION
        ConfigurationItemType.LastScreenOnTime -> LAST_SCREEN_ON_TIME
    }

    fun parse(value: Int) = when(value) {
        OWN_DEVICE_ID -> ConfigurationItemType.OwnDeviceId
        SHOWN_HINTS -> ConfigurationItemType.ShownHints
        WAS_DEVICE_LOCKED -> ConfigurationItemType.WasDeviceLocked
        FOREGROUND_APP_QUERY_RANGE -> ConfigurationItemType.ForegroundAppQueryRange
        ENABLE_ALTERNATIVE_DURATION_SELECTION -> ConfigurationItemType.EnableAlternativeDurationSelection
        LAST_SCREEN_ON_TIME -> ConfigurationItemType.LastScreenOnTime
        else -> throw IllegalArgumentException()
    }
}

class ConfigurationItemTypeConverter {
    @TypeConverter
    fun toInt(value: ConfigurationItemType) = ConfigurationItemTypeUtil.serialize(value)

    @TypeConverter
    fun toConfigurationItemType(value: Int) = ConfigurationItemTypeUtil.parse(value)
}

object HintsToShow {
    const val OVERVIEW_INTRODUCTION = 1L
    const val DEVICE_SCREEN_INTRODUCTION = 2L
    const val CATEGORIES_INTRODUCTION = 4L
    const val TIME_LIMIT_RULE_INTRODUCTION = 8L
    const val CONTACTS_INTRO = 16L
    const val TIMELIMIT_RULE_MUSTREAD = 32L
}
