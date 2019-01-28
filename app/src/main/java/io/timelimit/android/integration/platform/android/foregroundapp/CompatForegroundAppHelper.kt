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

import android.app.ActivityManager
import android.content.Context
import io.timelimit.android.integration.platform.RuntimePermissionStatus

class CompatForegroundAppHelper(context: Context) : ForegroundAppHelper() {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    override suspend fun getForegroundAppPackage(): String? {
        return try {
            activityManager.getRunningTasks(1)[0].topActivity.packageName
        } catch (ex: NullPointerException) {
            null
        }
    }

    override fun getPermissionStatus(): RuntimePermissionStatus {
        return RuntimePermissionStatus.NotRequired
    }
}
