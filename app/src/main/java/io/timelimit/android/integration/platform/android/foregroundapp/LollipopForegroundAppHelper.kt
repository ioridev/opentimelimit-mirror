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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import io.timelimit.android.integration.platform.RuntimePermissionStatus

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LollipopForegroundAppHelper(private val context: Context) : ForegroundAppHelper() {
    private val usageStatsManager = context.getSystemService(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) Context.USAGE_STATS_SERVICE else "usagestats") as UsageStatsManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    @Throws(SecurityException::class)
    override fun getForegroundAppPackage(): String? {
        if (getPermissionStatus() == RuntimePermissionStatus.NotGranted) {
            throw SecurityException()
        }

        val time = System.currentTimeMillis()
        // query data for last 7 days
        val usageEvents = usageStatsManager.queryEvents(time - 1000 * 60 * 60 * 24 * 7, time)

        if (usageEvents != null) {
            val event = UsageEvents.Event()

            var lastTime: Long = 0
            var lastPackage: String? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > lastTime) {
                        lastTime = event.timeStamp
                        lastPackage = event.packageName
                    }
                }
            }

            return lastPackage
        }

        return null
    }

    override fun getPermissionStatus(): RuntimePermissionStatus {
        if(appOpsManager.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED) {
            return RuntimePermissionStatus.Granted
        } else {
            return RuntimePermissionStatus.NotGranted
        }
    }
}
