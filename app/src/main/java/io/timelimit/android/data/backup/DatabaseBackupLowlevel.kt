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
package io.timelimit.android.data.backup

import android.util.JsonReader
import android.util.JsonWriter
import io.timelimit.android.data.Database
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.data.model.*
import io.timelimit.android.data.transaction
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

object DatabaseBackupLowlevel {
    private const val PAGE_SIZE = 50

    private const val APP = "app"
    private const val CATEGORY = "category"
    private const val CATEGORY_APP = "categoryApp"
    private const val CONFIG = "config"
    private const val DEVICE = "device"
    private const val TIME_LIMIT_RULE = "timelimitRule"
    private const val USED_TIME_ITEM = "usedTime"
    private const val USER = "user"
    private const val APP_ACTIVITY = "appActivity"
    private const val ALLOWED_CONTACT = "allowedContact"
    private const val USER_KEY = "userKey"

    fun outputAsBackupJson(database: Database, outputStream: OutputStream) {
        val writer = JsonWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))

        writer.beginObject()

        fun <T: JsonSerializable> handleCollection(
                name: String,
                readPage: (offset: Int, pageSize: Int) -> List<T>
        ) {
            writer.name(name).beginArray()

            var offset = 0

            while (true) {
                val page = readPage(offset, PAGE_SIZE)
                offset += page.size

                if (page.isEmpty()) {
                    break
                }

                page.forEach { it.serialize(writer) }
            }

            writer.endArray()
        }

        handleCollection(APP) {offset, pageSize -> database.app().getAppPageSync(offset, pageSize) }
        handleCollection(CATEGORY) {offset: Int, pageSize: Int -> database.category().getCategoryPageSync(offset, pageSize) }
        handleCollection(CATEGORY_APP) { offset, pageSize -> database.categoryApp().getCategoryAppPageSync(offset, pageSize) }

        writer.name(CONFIG).beginArray()
        database.config().getConfigItemsSync().forEach { it.serialize(writer) }
        writer.endArray()

        handleCollection(DEVICE) { offset, pageSize -> database.device().getDevicePageSync(offset, pageSize) }
        handleCollection(TIME_LIMIT_RULE) { offset, pageSize -> database.timeLimitRules().getRulePageSync(offset, pageSize) }
        handleCollection(USED_TIME_ITEM) { offset, pageSize -> database.usedTimes().getUsedTimePageSync(offset, pageSize) }
        handleCollection(USER) { offset, pageSize -> database.user().getUserPageSync(offset, pageSize) }
        handleCollection(APP_ACTIVITY) { offset, pageSize -> database.appActivity().getAppActivityPageSync(offset, pageSize) }
        handleCollection(ALLOWED_CONTACT) { offset, pageSize -> database.allowedContact().getAllowedContactPageSync(offset, pageSize) }
        handleCollection(USER_KEY) { offset, pageSize -> database.userKey().getUserKeyPageSync(offset, pageSize) }

        writer.endObject().flush()
    }

    fun restoreFromBackupJson(database: Database, inputStream: InputStream) {
        val reader = JsonReader(InputStreamReader(inputStream, Charsets.UTF_8))

        database.transaction().use {
            transaction ->

            database.deleteAllData()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    APP -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.app().addAppSync(App.parse(reader))
                        }

                        reader.endArray()
                    }
                    CATEGORY -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.category().addCategory(Category.parse(reader))
                        }

                        reader.endArray()
                    }
                    CATEGORY_APP -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.categoryApp().addCategoryAppSync(CategoryApp.parse(reader))
                        }

                        reader.endArray()
                    }
                    CONFIG -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            val item = ConfigurationItem.parse(reader)

                            if (item != null) {
                                database.config().updateValueOfKeySync(item)
                            }
                        }

                        reader.endArray()
                    }
                    DEVICE -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.device().addDeviceSync(Device.parse(reader))
                        }

                        reader.endArray()
                    }
                    TIME_LIMIT_RULE -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.timeLimitRules().addTimeLimitRule(TimeLimitRule.parse(reader))
                        }

                        reader.endArray()
                    }
                    USED_TIME_ITEM -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.usedTimes().insertUsedTime(UsedTimeItem.parse(reader))
                        }

                        reader.endArray()
                    }
                    USER -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.user().addUserSync(User.parse(reader))
                        }

                        reader.endArray()
                    }
                    APP_ACTIVITY -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.appActivity().addAppActivitySync(AppActivity.parse(reader))
                        }

                        reader.endArray()
                    }
                    ALLOWED_CONTACT -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.allowedContact().addContactSync(
                                    // this will use an unused id
                                    AllowedContact.parse(reader).copy(id = 0)
                            )
                        }

                        reader.endArray()
                    }
                    USER_KEY -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.userKey().addUserKeySync(UserKey.parse(reader))
                        }

                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            transaction.setSuccess()
        }
    }
}
