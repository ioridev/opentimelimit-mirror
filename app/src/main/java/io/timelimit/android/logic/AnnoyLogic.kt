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
package io.timelimit.android.logic

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.livedata.and
import io.timelimit.android.livedata.invert
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map

class AnnoyLogic (val appLogic: AppLogic) {
    // config
    companion object {
        private const val TEMP_UNBLOCK_DURATION = 1000 * 45L
        private const val TEMP_UNBLOCK_PARENT_DURATION = 1000 * 60 * 10L
        private const val MAX_BLOCK_DURATION = 15

        fun manualUnblockDelay(counter: Int): Long {
            return if (counter <= 0) 0
            else (counter + 4).coerceAtMost(MAX_BLOCK_DURATION).toLong() * 1000 * 60
        }
    }

    // input: clock
    private fun now() = appLogic.timeApi.getCurrentUptimeInMillis()

    // input: is manipulated (bool)
    private val isManipulated = appLogic.deviceEntryIfEnabled.map { it?.hasActiveManipulationWarning ?: false }
    private val isDeviceOwner = appLogic.deviceEntryIfEnabled.map { it?.currentProtectionLevel == ProtectionLevel.DeviceOwner }
    private val enableAnnoy = appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.MANIPULATION_ANNOY_USER)
    private val enableAnnoyNow = isManipulated.and(isDeviceOwner).and(enableAnnoy)

    private val annoyTempDisabled = MutableLiveData<Boolean>().apply { value = false }
    private val annoyTempDisabledSetFalse: Runnable = Runnable {
        annoyTempDisabled.value = false
        isManipulated.removeObserver(resetTempDisabledObserver)
    }
    private val resetTempDisabledObserver = Observer<Boolean> { isManipulated ->
        if (!isManipulated) {
            Threads.mainThreadHandler.removeCallbacks(annoyTempDisabledSetFalse)
            Threads.mainThreadHandler.post(annoyTempDisabledSetFalse)
        }
    }

    // output: should block right now
    val shouldAnnoyRightNow = enableAnnoyNow.and(annoyTempDisabled.invert())

    // state: block duration
    private var nextManualUnblockTimestamp = now()
    private var manualUnblockCounter = 0

    // output: duration until next manual unblock
    val nextManualUnblockCountdown = liveDataFromFunction {
        (nextManualUnblockTimestamp - now()).coerceAtLeast(0)
    }

    // input: trigger temp unblock (event)
    fun doManualTempUnlock() {
        val now = now()

        if (now < nextManualUnblockTimestamp) return
        if (annoyTempDisabled.value == true) return

        // eventually reset
        if (nextManualUnblockTimestamp + manualUnblockDelay(manualUnblockCounter) < now) {
            manualUnblockCounter = 1
        } else {
            manualUnblockCounter += 1
        }

        nextManualUnblockTimestamp = now + manualUnblockDelay(manualUnblockCounter)

        enableTempDisabled(TEMP_UNBLOCK_DURATION)
    }

    // input: trigger temp unblock by parents (event)
    fun doParentTempUnlock() {
        manualUnblockCounter = 0
        nextManualUnblockTimestamp = now()

        enableTempDisabled(TEMP_UNBLOCK_PARENT_DURATION)
    }

    private fun enableTempDisabled(duration: Long) {
        annoyTempDisabled.value = true

        Threads.mainThreadHandler.removeCallbacks(annoyTempDisabledSetFalse)
        Threads.mainThreadHandler.postDelayed(annoyTempDisabledSetFalse, duration)
        isManipulated.observeForever(resetTempDisabledObserver)
    }
}