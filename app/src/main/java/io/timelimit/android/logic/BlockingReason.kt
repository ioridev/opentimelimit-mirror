/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.model.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.extension.isCategoryAllowed
import java.util.*

enum class BlockingReason {
    None,
    NotPartOfAnCategory,
    TemporarilyBlocked,
    BlockedAtThisTime,
    TimeOver,
    TimeOverExtraTimeCanBeUsedLater,
    NotificationsAreBlocked,
    BatteryLimit,
    SessionDurationLimit
}

enum class BlockingLevel {
    App,
    Activity
}

sealed class BlockingReasonDetail {
    abstract val areNotificationsBlocked: Boolean
}
data class NoBlockingReason(
        override val areNotificationsBlocked: Boolean
): BlockingReasonDetail() {
    companion object {
        private val instanceWithoutNotificationsBlocked = NoBlockingReason(areNotificationsBlocked = false)
        private val instanceWithNotificationsBlocked = NoBlockingReason(areNotificationsBlocked = true)

        fun getInstance(areNotificationsBlocked: Boolean) = if (areNotificationsBlocked)
            instanceWithNotificationsBlocked
        else
            instanceWithoutNotificationsBlocked
    }
}
data class BlockedReasonDetails(
        val reason: BlockingReason,
        val level: BlockingLevel,
        val categoryId: String?,
        override val areNotificationsBlocked: Boolean
): BlockingReasonDetail()

class BlockingReasonUtil(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "BlockingReason"
    }

    private val enableActivityLevelFiltering = appLogic.deviceEntry.map { it?.enableActivityLevelBlocking ?: false }
    private val batteryLevel = appLogic.platformIntegration.getBatteryStatusLive()

    fun getBlockingReason(packageName: String, activityName: String?): LiveData<BlockingReasonDetail> {
        // check precondition that the app is running

        return appLogic.enable.switchMap {
            enabled ->

            if (enabled == null || enabled == false) {
                liveDataFromValue(NoBlockingReason.getInstance(areNotificationsBlocked = false) as BlockingReasonDetail)
            } else {
                appLogic.deviceUserEntry.switchMap {
                    user ->

                    if (user == null || user.type != UserType.Child) {
                        liveDataFromValue(NoBlockingReason.getInstance(areNotificationsBlocked = false) as BlockingReasonDetail)
                    } else {
                        getBlockingReasonStep2(packageName, activityName, user, TimeZone.getTimeZone(user.timeZone))
                    }
                }
            }
        }
    }

    private fun getBlockingReasonStep2(packageName: String, activityName: String?, child: User, timeZone: TimeZone): LiveData<BlockingReasonDetail> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 2")
        }

        // check internal whitelist
        if (packageName == BuildConfig.APPLICATION_ID) {
            return liveDataFromValue(NoBlockingReason.getInstance(areNotificationsBlocked = false))
        } else if (AndroidIntegrationApps.ignoredApps.contains(packageName)) {
            return liveDataFromValue(NoBlockingReason.getInstance(areNotificationsBlocked = false))
        } else {
            return getBlockingReasonStep3(packageName, activityName, child, timeZone)
        }
    }

    private fun getBlockingReasonStep3(packageName: String, activityName: String?, child: User, timeZone: TimeZone): LiveData<BlockingReasonDetail> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 3")
        }

        // check temporarily allowed Apps
        return appLogic.deviceId.switchMap {
            if (it != null) {
                appLogic.database.temporarilyAllowedApp().getTemporarilyAllowedApps()
            } else {
                liveDataFromValue(Collections.emptyList())
            }
        }.switchMap {
            temporarilyAllowedApps ->

            if (temporarilyAllowedApps.contains(packageName)) {
                liveDataFromValue(NoBlockingReason.getInstance(areNotificationsBlocked = false) as BlockingReasonDetail)
            } else {
                getBlockingReasonStep4(packageName, activityName, child, timeZone)
            }
        }
    }

    private fun getBlockingReasonStep4(packageName: String, activityName: String?, child: User, timeZone: TimeZone): LiveData<BlockingReasonDetail> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 4")
        }

        return appLogic.database.category().getCategoriesByChildId(child.id).switchMap {
            childCategories ->

            val categoryAppLevel = appLogic.database.categoryApp().getCategoryApp(childCategories.map { it.id }, packageName)
            val categoryAppActivityLevel = enableActivityLevelFiltering.switchMap {
                if (it)
                    appLogic.database.categoryApp().getCategoryApp(childCategories.map { it.id }, "$packageName:$activityName")
                else
                    liveDataFromValue(null as CategoryApp?)
            }

            val categoryApp = categoryAppLevel.switchMap { appLevel ->
                categoryAppActivityLevel.map { activityLevel ->
                    activityLevel?.let { it to BlockingLevel.Activity } ?: appLevel?.let { it to BlockingLevel.App }
                }
            }

            Transformations.map(categoryApp) {
                categoryApp ->

                if (categoryApp == null) {
                    null
                } else {
                    childCategories.find { it.id == categoryApp.first.categoryId }?.let { it to categoryApp.second }
                }
            }
        }.switchMap {
            categoryEntry ->

            if (categoryEntry == null) {
                val defaultCategory = if (child.categoryForNotAssignedApps.isEmpty())
                    liveDataFromValue(null as Category?)
                else
                    appLogic.database.category().getCategoryByChildIdAndId(child.id, child.categoryForNotAssignedApps)

                defaultCategory.switchMap { categoryEntry2 ->
                    if (categoryEntry2 == null) {
                        liveDataFromValue(
                                BlockedReasonDetails(
                                        areNotificationsBlocked = false,
                                        level = BlockingLevel.App,
                                        reason = BlockingReason.NotPartOfAnCategory,
                                        categoryId = null
                                ) as BlockingReasonDetail
                        )
                    } else {
                        getBlockingReasonStep4Point5(categoryEntry2, child, timeZone, false, BlockingLevel.App)
                    }
                }
            } else {
                getBlockingReasonStep4Point5(categoryEntry.first, child, timeZone, false, categoryEntry.second)
            }
        }
    }

    private fun getBlockingReasonStep4Point5(category: Category, child: User, timeZone: TimeZone, isParentCategory: Boolean, blockingLevel: BlockingLevel): LiveData<BlockingReasonDetail> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 4.5")
        }

        val blockNotifications = category.blockAllNotifications

        val nextLevel = getBlockingReasonStep4Point6(category, child, timeZone, isParentCategory, blockingLevel)

        return nextLevel.map { blockingReason ->
            if (blockingReason == BlockingReason.None) {
                NoBlockingReason.getInstance(areNotificationsBlocked = blockNotifications)
            } else {
                BlockedReasonDetails(
                        areNotificationsBlocked = blockNotifications,
                        level = blockingLevel,
                        reason = blockingReason,
                        categoryId = category.id
                )
            }
        }
    }

    private fun getBlockingReasonStep4Point6(category: Category, child: User, timeZone: TimeZone, isParentCategory: Boolean, blockingLevel: BlockingLevel): LiveData<BlockingReason> {
        val next = getBlockingReasonStep4Point7(category, child, timeZone, isParentCategory, blockingLevel)

        return if (category.minBatteryLevelWhileCharging == 0 && category.minBatteryLevelMobile == 0) {
            next
        } else {
            val batteryLevelOk = batteryLevel.map { it.isCategoryAllowed(category) }.ignoreUnchanged()

            batteryLevelOk.switchMap { ok ->
                if (ok) {
                    next
                } else {
                    liveDataFromValue(BlockingReason.BatteryLimit)
                }
            }
        }
    }

    private fun getBlockingReasonStep4Point7(category: Category, child: User, timeZone: TimeZone, isParentCategory: Boolean, blockingLevel: BlockingLevel): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 4.7")
        }

        if (category.temporarilyBlocked) {
            if (category.temporarilyBlockedEndTime == 0L) {
                return liveDataFromValue(BlockingReason.TemporarilyBlocked)
            } else {
                return getTemporarilyTrustedTimeInMillis().switchMap { time ->
                    if (time < category.temporarilyBlockedEndTime) {
                        liveDataFromValue(BlockingReason.TemporarilyBlocked)
                    } else {
                        getBlockingReasonStep4Point8(category, child, timeZone, isParentCategory, blockingLevel)
                    }
                }
            }
        } else {
            return getBlockingReasonStep4Point8(category, child, timeZone, isParentCategory, blockingLevel)
        }
    }

    private fun getBlockingReasonStep4Point8(category: Category, child: User, timeZone: TimeZone, isParentCategory: Boolean, blockingLevel: BlockingLevel): LiveData<BlockingReason> {
        val areLimitsDisabled: LiveData<Boolean>

        if (child.disableLimitsUntil == 0L) {
            areLimitsDisabled = liveDataFromValue(false)
        } else {
            areLimitsDisabled = getTemporarilyTrustedTimeInMillis().map {
                trustedTimeInMillis ->

                child.disableLimitsUntil > trustedTimeInMillis
            }
        }

        return areLimitsDisabled.switchMap {
            limitsDisabled ->

            if (limitsDisabled) {
                liveDataFromValue(BlockingReason.None)
            } else {
                getBlockingReasonStep5(category, timeZone)
            }
        }.switchMap { result ->
            if (result == BlockingReason.None && (!isParentCategory) && category.parentCategoryId.isNotEmpty()) {
                appLogic.database.category().getCategoryByChildIdAndId(child.id, category.parentCategoryId).switchMap { parentCategory ->
                    if (parentCategory == null) {
                        liveDataFromValue(BlockingReason.None)
                    } else {
                        getBlockingReasonStep4Point6(parentCategory, child, timeZone, true, blockingLevel)
                    }
                }
            } else {
                liveDataFromValue(result)
            }
        }
    }

    private fun getBlockingReasonStep5(category: Category, timeZone: TimeZone): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 5")
        }

        return Transformations.switchMap(getTrustedMinuteOfWeekLive(timeZone)) {
            trustedMinuteOfWeek ->

            if (category.blockedMinutesInWeek.dataNotToModify.isEmpty) {
                getBlockingReasonStep6(category, timeZone, trustedMinuteOfWeek)
            } else if (category.blockedMinutesInWeek.read(trustedMinuteOfWeek)) {
                liveDataFromValue(BlockingReason.BlockedAtThisTime)
            } else {
                getBlockingReasonStep6(category, timeZone, trustedMinuteOfWeek)
            }
        }
    }

    private fun getBlockingReasonStep6(category: Category, timeZone: TimeZone, trustedMinuteOfWeek: Int): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 6")
        }

        return getTrustedDateLive(timeZone).switchMap {
            nowTrustedDate ->

            appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(category.id).switchMap {
                rules ->

                if (rules.isEmpty()) {
                    liveDataFromValue(BlockingReason.None)
                } else {
                    getBlockingReasonStep6(category, nowTrustedDate, trustedMinuteOfWeek, rules)
                }
            }
        }
    }

    private fun getBlockingReasonStep6(category: Category, nowTrustedDate: DateInTimezone, trustedMinuteOfWeek: Int, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 6 - 2")
        }

        return getBlockingReasonStep7(category, nowTrustedDate, trustedMinuteOfWeek, rules)
    }

    private fun getBlockingReasonStep7(category: Category, nowTrustedDate: DateInTimezone, trustedMinuteOfWeek: Int, rules: List<TimeLimitRule>): LiveData<BlockingReason> {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "step 7")
        }

        val extraTime = category.getExtraTime(dayOfEpoch = nowTrustedDate.dayOfEpoch)
        val firstDayOfWeekAsEpochDay = nowTrustedDate.dayOfEpoch - nowTrustedDate.dayOfWeek

        return appLogic.database.usedTimes().getUsedTimesOfWeek(category.id, nowTrustedDate.dayOfEpoch - nowTrustedDate.dayOfWeek).switchMap {
            usedTimes ->
            val remaining = RemainingTime.getRemainingTime(nowTrustedDate.dayOfWeek, trustedMinuteOfWeek % MinuteOfDay.LENGTH, usedTimes, rules, extraTime, firstDayOfWeekAsEpochDay)

            if (remaining == null || remaining.includingExtraTime > 0) {
                appLogic.database.sessionDuration().getSessionDurationItemsByCategoryId(category.id).switchMap { durations ->
                    getTemporarilyTrustedTimeInMillis().map { timeInMillis ->
                        val remainingDuration = RemainingSessionDuration.getRemainingSessionDuration(
                                rules = rules,
                                dayOfWeek = nowTrustedDate.dayOfWeek,
                                durationsOfCategory = durations,
                                minuteOfDay = trustedMinuteOfWeek % MinuteOfDay.LENGTH,
                                timestamp = timeInMillis
                        )

                        if (remainingDuration == null || remainingDuration > 0) {
                            BlockingReason.None
                        } else {
                            BlockingReason.SessionDurationLimit
                        }
                    }
                }
            } else {
                if (extraTime > 0) {
                    liveDataFromValue(BlockingReason.TimeOverExtraTimeCanBeUsedLater)
                } else {
                    liveDataFromValue(BlockingReason.TimeOver)
                }
            }
        }
    }

    fun getTemporarilyTrustedTimeInMillis(): LiveData<Long> {
        return liveDataFromFunction {
            appLogic.timeApi.getCurrentTimeInMillis()
        }
    }

    fun getTrustedMinuteOfWeekLive(timeZone: TimeZone): LiveData<Int> {
        return object: LiveData<Int>() {
            fun update() {
                val timeInMillis = appLogic.timeApi.getCurrentTimeInMillis()

                value = getMinuteOfWeek(timeInMillis, timeZone)
            }

            init {
                update()
            }

            val scheduledUpdateRunnable = Runnable {
                update()
                scheduleUpdate()
            }

            fun scheduleUpdate() {
                appLogic.timeApi.runDelayed(scheduledUpdateRunnable, 1000L /* every second */)
            }

            fun cancelScheduledUpdate() {
                appLogic.timeApi.cancelScheduledAction(scheduledUpdateRunnable)
            }

            override fun onActive() {
                super.onActive()

                update()
                scheduleUpdate()
            }

            override fun onInactive() {
                super.onInactive()

                cancelScheduledUpdate()
            }
        }.ignoreUnchanged()
    }

    fun getTrustedDateLive(timeZone: TimeZone): LiveData<DateInTimezone> {
        return object: LiveData<DateInTimezone>() {
            fun update() {
                val timeInMillis = appLogic.timeApi.getCurrentTimeInMillis()

                value = DateInTimezone.newInstance(timeInMillis, timeZone)
            }

            init {
                update()
            }

            val scheduledUpdateRunnable = Runnable {
                update()
                scheduleUpdate()
            }

            fun scheduleUpdate() {
                appLogic.timeApi.runDelayed(scheduledUpdateRunnable, 1000L /* every second */)
            }

            fun cancelScheduledUpdate() {
                appLogic.timeApi.cancelScheduledAction(scheduledUpdateRunnable)
            }

            override fun onActive() {
                super.onActive()

                update()
                scheduleUpdate()
            }

            override fun onInactive() {
                super.onInactive()

                cancelScheduledUpdate()
            }
        }.ignoreUnchanged()
    }
}
