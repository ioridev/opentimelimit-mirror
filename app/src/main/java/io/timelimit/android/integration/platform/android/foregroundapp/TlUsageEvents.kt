/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.app.usage.UsageEvents
import android.content.res.Configuration
import android.os.Parcel

/**
 * This class is/should be a Parcel compatible clone of the UsageEvents
 * system class. This allows reading fields which the public API does not provide.
 */
class TlUsageEvents (private val content: Parcel) {
    companion object {
        const val NONE = 0
        const val MOVE_TO_FOREGROUND = 1
        const val MOVE_TO_BACKGROUND = 2
        // const val END_OF_DAY = 3
        // const val CONTINUE_PREVIOUS_DAY = 4
        const val CONFIGURATION_CHANGE = 5
        // const val SYSTEM_INTERACTION = 6
        // const val USER_INTERACTION = 7
        const val SHORTCUT_INVOCATION = 8
        const val CHOOSER_ACTION = 9
        // const val NOTIFICATION_SEEN = 10
        const val STANDBY_BUCKET_CHANGED = 11
        const val NOTIFICATION_INTERRUPTION = 12
        // const val SLICE_PINNED_PRIV = 13
        // const val SLICE_PINNED = 14
        // const val SCREEN_INTERACTIVE = 15
        // const val SCREEN_NON_INTERACTIVE = 16
        // const val KEYGUARD_SHOWN = 17
        // const val KEYGUARD_HIDDEN = 18
        // const val FOREGROUND_SERVICE_START = 19
        // const val FOREGROUND_SERVICE_STOP = 20
        // const val CONTINUING_FOREGROUND_SERVICE = 21
        // const val ROLLOVER_FOREGROUND_SERVICE = 22
        const val ACTIVITY_STOPPED = 23
        // const val ACTIVITY_DESTROYED = 24
        // const val FLUSH_TO_DISK = 25
        // const val DEVICE_SHUTDOWN = 26
        const val DEVICE_STARTUP = 27
        // const val USER_UNLOCKED = 28
        // const val USER_STOPPED = 29
        const val LOCUS_ID_SET = 30
        // const val APP_COMPONENT_USED = 31
        const val MAX_EVENT_TYPE = 31
        const val DUMMY_STRING = "null"

        fun getParcel(input: UsageEvents): Parcel {
            val outerParcel = Parcel.obtain()

            val blob = try {
                input.writeToParcel(outerParcel, 0)
                outerParcel.setDataPosition(0)

                ParcelBlob.readBlob(outerParcel)
            } finally {
                outerParcel.recycle()
            }

            val innerParcel = Parcel.obtain()

            try {
                innerParcel.unmarshall(blob, 0, blob.size)
                innerParcel.setDataPosition(0)

                return innerParcel
            } catch (ex: Exception) {
                innerParcel.recycle()

                throw ex
            }
        }

        fun fromUsageEvents(input: UsageEvents): TlUsageEvents = TlUsageEvents(getParcel(input))
    }

    private var free = false
    private val length = content.readInt()
    private var index = content.readInt()
    private val strings = if (length > 0) content.createStringArray() else null

    init {
        if (length > 0) {
            val listByteLength = content.readInt()
            val positionInParcel = content.readInt()

            content.setDataPosition(content.dataPosition() + positionInParcel)
            content.setDataSize(content.dataPosition() + listByteLength)
        } else {
            free()
        }
    }

    private var outputTimestamp = 0L
    private var outputEventType = 0
    private var outputInstanceId = 0
    private var outputPackageName = DUMMY_STRING
    private var outputClassName = DUMMY_STRING

    val timestamp get() = outputTimestamp
    val eventType get() = outputEventType
    val instanceId get() = outputInstanceId
    val packageName get() = outputPackageName
    val className get() = outputClassName

    fun readNextItem(): Boolean {
        if (free) return false
        if (strings == null) throw IllegalStateException()

        val packageIndex = content.readInt()
        val classIndex = content.readInt()
        val instanceId = content.readInt()
        val taskRootPackageIndex = content.readInt()
        val taskRootClassIndex = content.readInt()
        val eventType = content.readInt()
        val timestamp = content.readLong()

        if (eventType < NONE || eventType > MAX_EVENT_TYPE) {
            throw UnknownEventTypeException()
        }

        when (eventType) {
            CONFIGURATION_CHANGE -> {
                val newConfiguration = Configuration.CREATOR.createFromParcel(content)
            }
            SHORTCUT_INVOCATION -> {
                val shortcutId = content.readString()
            }
            CHOOSER_ACTION -> {
                val action = content.readString()
                val contentType = content.readString()
                val contentAnnotations = content.createStringArray()
            }
            STANDBY_BUCKET_CHANGED -> {
                val bucketAndReason = content.readInt()
            }
            NOTIFICATION_INTERRUPTION -> {
                val notificationChannelId = content.readString()
            }
            LOCUS_ID_SET -> {
                val locusId = content.readString()
            }
        }
        val flags = content.readInt()

        outputTimestamp = timestamp
        outputEventType = eventType
        outputInstanceId = instanceId
        outputPackageName = if (packageIndex == -1) DUMMY_STRING else strings[packageIndex]
        outputClassName = if(classIndex == -1) DUMMY_STRING else strings[classIndex]

        index++; if (index == length) free()

        return true
    }

    fun free() {
        if (!free) {
            content.recycle()

            free = true
        }
    }

    open class UsageException: RuntimeException()
    class UnknownEventTypeException: UsageException()
}