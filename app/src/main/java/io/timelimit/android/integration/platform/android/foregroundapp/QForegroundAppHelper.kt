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
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.android.foregroundapp.usagestats.DirectUsageStatsReader
import java.security.SecureRandom

class QForegroundAppHelper(context: Context): UsageStatsForegroundAppHelper(context) {
    private val legacy = LollipopForegroundAppHelper(context)
    private val modern = InstanceIdForegroundAppHelper(context)
    private val forceNewMethod = SecureRandom().nextBoolean()

    override suspend fun getForegroundApps(
        queryInterval: Long,
        experimentalFlags: Long
    ): Set<ForegroundApp> {
        val canUseModern = DirectUsageStatsReader.instanceIdSupported
        val didUserRequestModern = experimentalFlags and ExperimentalFlags.INSTANCE_ID_FG_APP_DETECTION == ExperimentalFlags.INSTANCE_ID_FG_APP_DETECTION
        val useModern = forceNewMethod || didUserRequestModern

        return if (canUseModern && useModern) modern.getForegroundApps(queryInterval, experimentalFlags)
        else legacy.getForegroundApps(queryInterval, experimentalFlags)
    }
}