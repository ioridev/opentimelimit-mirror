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
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson

@Entity(tableName = "category")
@TypeConverters(ImmutableBitmaskAdapter::class)
data class Category(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "child_id")
        val childId: String,
        @ColumnInfo(name = "title")
        val title: String,
        @ColumnInfo(name = "blocked_times")
        val blockedMinutesInWeek: ImmutableBitmask,    // 10080 bit -> ~10 KB
        @ColumnInfo(name = "extra_time")
        val extraTimeInMillis: Long,
        @ColumnInfo(name = "temporarily_blocked")
        val temporarilyBlocked: Boolean,
        @ColumnInfo(name = "parent_category_id")
        val parentCategoryId: String
): JsonSerializable {
    companion object {
        const val MINUTES_PER_DAY = 60 * 24
        const val BLOCKED_MINUTES_IN_WEEK_LENGTH = MINUTES_PER_DAY * 7

        private const val ID = "id"
        private const val CHILD_ID = "childId"
        private const val TITLE = "title"
        private const val BLOCKED_MINUTES_IN_WEEK = "blockedMinutesInWeek"
        private const val EXTRA_TIME_IN_MILLIS = "extraTimeInMillis"
        private const val TEMPORARILY_BLOCKED = "temporarilyBlocked"
        private const val PARENT_CATEGORY_ID = "parentCategoryId"

        fun parse(reader: JsonReader): Category {
            var id: String? = null
            var childId: String? = null
            var title: String? = null
            var blockedMinutesInWeek: ImmutableBitmask? = null
            var extraTimeInMillis: Long? = null
            var temporarilyBlocked: Boolean? = null
            // this field was added later so it has got a default value
            var parentCategoryId = ""

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    CHILD_ID -> childId = reader.nextString()
                    TITLE -> title = reader.nextString()
                    BLOCKED_MINUTES_IN_WEEK -> blockedMinutesInWeek = ImmutableBitmaskJson.parse(reader.nextString(), BLOCKED_MINUTES_IN_WEEK_LENGTH)
                    EXTRA_TIME_IN_MILLIS -> extraTimeInMillis = reader.nextLong()
                    TEMPORARILY_BLOCKED -> temporarilyBlocked = reader.nextBoolean()
                    PARENT_CATEGORY_ID -> parentCategoryId = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return Category(
                    id = id!!,
                    childId = childId!!,
                    title = title!!,
                    blockedMinutesInWeek = blockedMinutesInWeek!!,
                    extraTimeInMillis = extraTimeInMillis!!,
                    temporarilyBlocked = temporarilyBlocked!!,
                    parentCategoryId = parentCategoryId
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)
        IdGenerator.assertIdValid(childId)

        if (extraTimeInMillis < 0) {
            throw IllegalStateException()
        }

        if (title.isEmpty()) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(CHILD_ID).value(childId)
        writer.name(TITLE).value(title)
        writer.name(BLOCKED_MINUTES_IN_WEEK).value(ImmutableBitmaskJson.serialize(blockedMinutesInWeek))
        writer.name(EXTRA_TIME_IN_MILLIS).value(extraTimeInMillis)
        writer.name(TEMPORARILY_BLOCKED).value(temporarilyBlocked)
        writer.name(PARENT_CATEGORY_ID).value(parentCategoryId)

        writer.endObject()
    }
}
