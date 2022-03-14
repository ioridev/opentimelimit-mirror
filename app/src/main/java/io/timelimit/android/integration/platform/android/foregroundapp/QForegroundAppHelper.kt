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

import android.content.Context
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.integration.platform.ForegroundApp

class QForegroundAppHelper(context: Context): UsageStatsForegroundAppHelper(context) {
    companion object {
        private const val LOG_TAG = "QForegroundAppHelper"
    }

    private val legacy = LollipopForegroundAppHelper(context)
    private val modern = InstanceIdForegroundAppHelper(context)
    private var fallbackCounter = 0

    override suspend fun getForegroundApps(
        queryInterval: Long,
        experimentalFlags: Long
    ): Set<ForegroundApp> {
        val useInstanceIdForegroundAppDetection = experimentalFlags and ExperimentalFlags.INSTANCE_ID_FG_APP_DETECTION == ExperimentalFlags.INSTANCE_ID_FG_APP_DETECTION
        val disableFallback = experimentalFlags and ExperimentalFlags.DISABLE_FG_APP_DETECTION_FALLBACK == ExperimentalFlags.DISABLE_FG_APP_DETECTION_FALLBACK

        val result = if (useInstanceIdForegroundAppDetection && disableFallback) {
            modern.getForegroundApps(queryInterval, experimentalFlags)
        } else if (useInstanceIdForegroundAppDetection && fallbackCounter == 0) {
            try {
                modern.getForegroundApps(queryInterval, experimentalFlags)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "falling back to the legacy implementation", ex)
                }

                fallbackCounter = 100

                legacy.getForegroundApps(queryInterval, experimentalFlags)
            }
        } else {
            legacy.getForegroundApps(queryInterval, experimentalFlags)
        }

        if (fallbackCounter > 0) {
            fallbackCounter -= 1
        }

        return result
    }
}