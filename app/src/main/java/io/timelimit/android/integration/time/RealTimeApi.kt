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
package io.timelimit.android.integration.time

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.*

object RealTimeApi: TimeApi() {
    private val handler = Handler(Looper.getMainLooper())

    override fun getCurrentTimeInMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun getCurrentUptimeInMillis(): Long {
        return SystemClock.uptimeMillis()
    }

    override fun runDelayed(runnable: Runnable, delayInMillis: Long) {
        handler.postDelayed(runnable, delayInMillis)
    }

    override fun cancelScheduledAction(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }

    override fun getSystemTimeZone() = TimeZone.getDefault()
}
