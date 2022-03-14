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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.SparseArray
import androidx.core.util.size
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus

@TargetApi(Build.VERSION_CODES.Q)
class InstanceIdForegroundAppHelper(context: Context): UsageStatsForegroundAppHelper(context) {
    companion object {
        private const val START_QUERY_INTERVAL = 1000 * 60 * 60 * 24 * 3 // 3 days
        private const val TOLERANCE = 3000L
    }

    private var lastQueryTime = 0L
    private var lastEventTimestamp = 0L
    private val apps = SparseArray<ForegroundApp>()

    override suspend fun getForegroundApps(
        queryInterval: Long,
        experimentalFlags: Long
    ): Set<ForegroundApp> {
        if (Build.VERSION.SDK_INT > 32) {
            throw UntestedSystemVersionException()
        }

        if (getPermissionStatus() != RuntimePermissionStatus.Granted) {
            throw SecurityException()
        }

        val result = backgroundThread.executeAndWait {
            val now = System.currentTimeMillis()

            val didTimeWentBackwards = lastQueryTime > now
            val didNeverQuery = lastQueryTime == 0L
            val shouldDoFullQuery = didTimeWentBackwards || didNeverQuery

            if (shouldDoFullQuery) {
                apps.clear()
            }

            val minQueryStartTime = (now - START_QUERY_INTERVAL).coerceAtLeast(1)
            val queryStartTimeByLastEvent = lastEventTimestamp - TOLERANCE

            val queryStartTime = if (shouldDoFullQuery) {
                minQueryStartTime
            } else {
                queryStartTimeByLastEvent
                    .coerceAtLeast(minQueryStartTime)
                    .coerceAtMost(now - TOLERANCE)
            }

            val queryEndTime = now + TOLERANCE

            usageStatsManager.queryEvents(queryStartTime, queryEndTime)?.let { nativeEvents ->
                val events = TlUsageEvents.fromUsageEvents(nativeEvents)

                while (events.readNextItem()) {
                    lastEventTimestamp = events.timestamp

                    if (events.eventType == TlUsageEvents.DEVICE_STARTUP) {
                        apps.clear()
                    } else if (events.eventType == TlUsageEvents.MOVE_TO_FOREGROUND) {
                        val app = ForegroundApp(events.packageName, events.className)

                        apps.put(events.instanceId, app)
                    } else if (
                        events.eventType == TlUsageEvents.MOVE_TO_BACKGROUND ||
                        events.eventType == TlUsageEvents.ACTIVITY_STOPPED
                    ) {
                        apps.remove(events.instanceId)
                    }
                }
            }

            lastQueryTime = now

            val appsSet = mutableSetOf<ForegroundApp>()

            for (index in 0 until apps.size) {
                appsSet.add(apps.valueAt(index))
            }

            appsSet
        }

        return result
    }

    class UntestedSystemVersionException: RuntimeException()
}