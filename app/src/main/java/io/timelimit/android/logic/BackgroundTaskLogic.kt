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
package io.timelimit.android.logic

import android.util.Log
import android.util.SparseArray
import android.util.SparseLongArray
import androidx.lifecycle.LiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.data.model.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.integration.platform.AppStatusMessage
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.UpdateDeviceStatusAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.util.TimeTextUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class BackgroundTaskLogic(val appLogic: AppLogic) {
    companion object {
        private const val CHECK_PERMISSION_INTERVAL = 10 * 1000L    // all 10 seconds
        private const val BACKGROUND_SERVICE_INTERVAL = 100L    // all 100 ms
        private const val MAX_USED_TIME_PER_ROUND = 1000        // 1 second
        private const val LOG_TAG = "BackgroundTaskLogic"
    }

    private val temporarilyAllowedApps = appLogic.database.temporarilyAllowedApp().getTemporarilyAllowedApps()

    init {
        runAsyncExpectForever { backgroundServiceLoop() }
        runAsyncExpectForever { syncDeviceStatusLoop() }
        runAsyncExpectForever { backupDatabaseLoop() }
        runAsync {
            // this is effective after an reboot

            if (appLogic.deviceEntryIfEnabled.waitForNullableValue() != null) {
                appLogic.platformIntegration.setEnableSystemLockdown(true)
            }
        }

        appLogic.deviceEntryIfEnabled
                .map { it?.id }
                .ignoreUnchanged()
                .observeForever {
                    _ ->

                    runAsync {
                        syncInstalledAppVersion()
                    }
                }

        temporarilyAllowedApps.map { it.isNotEmpty() }.ignoreUnchanged().observeForever {
            appLogic.platformIntegration.setShowNotificationToRevokeTemporarilyAllowedApps(it!!)
        }
    }

    private val deviceUserEntryLive = SingleItemLiveDataCache(appLogic.deviceUserEntry.ignoreUnchanged())
    private val childCategories = object: MultiKeyLiveDataCache<List<Category>, String?>() {
        // key = child id
        override fun createValue(key: String?): LiveData<List<Category>> {
            if (key == null) {
                // this should rarely happen
                return liveDataFromValue(Collections.emptyList())
            } else {
                return appLogic.database.category().getCategoriesByChildId(key).ignoreUnchanged()
            }
        }
    }
    private val appCategories = object: MultiKeyLiveDataCache<CategoryApp?, Pair<String, List<String>>>() {
        // key = package name, category ids
        override fun createValue(key: Pair<String, List<String>>): LiveData<CategoryApp?> {
            return appLogic.database.categoryApp().getCategoryApp(key.second, key.first)
        }
    }
    private val timeLimitRules = object: MultiKeyLiveDataCache<List<TimeLimitRule>, String>() {
        override fun createValue(key: String): LiveData<List<TimeLimitRule>> {
            return appLogic.database.timeLimitRules().getTimeLimitRulesByCategory(key)
        }
    }
    private val usedTimesOfCategoryAndWeekByFirstDayOfWeek = object: MultiKeyLiveDataCache<SparseArray<UsedTimeItem>, Pair<String, Int>>() {
        override fun createValue(key: Pair<String, Int>): LiveData<SparseArray<UsedTimeItem>> {
            return appLogic.database.usedTimes().getUsedTimesOfWeek(key.first, key.second)
        }
    }

    private val liveDataCaches = LiveDataCaches(arrayOf(
            deviceUserEntryLive,
            childCategories,
            appCategories,
            timeLimitRules,
            usedTimesOfCategoryAndWeekByFirstDayOfWeek
    ))

    private var usedTimeUpdateHelper: UsedTimeItemBatchUpdateHelper? = null
    private var previousMainLogicExecutionTime = 0
    private var previousMainLoopEndTime = 0L

    private val appTitleCache = QueryAppTitleCache(appLogic.platformIntegration)

    private suspend fun backgroundServiceLoop() {
        while (true) {
            // app must be enabled
            if (!appLogic.enable.waitForNonNullValue()) {
                usedTimeUpdateHelper?.commit(appLogic)
                liveDataCaches.removeAllItems()
                appLogic.platformIntegration.setAppStatusMessage(null)
                appLogic.enable.waitUntilValueMatches { it == true }

                continue
            }

            // device must be used by a child
            val deviceUserEntry = deviceUserEntryLive.read().waitForNullableValue()

            if (deviceUserEntry == null || deviceUserEntry.type != UserType.Child) {
                usedTimeUpdateHelper?.commit(appLogic)
                liveDataCaches.removeAllItems()
                appLogic.platformIntegration.setAppStatusMessage(null)
                deviceUserEntryLive.read().waitUntilValueMatches { it != null && it.type == UserType.Child }

                continue
            }

            // loop logic
            try {
                // get the current time
                val nowTimestamp = appLogic.timeApi.getCurrentTimeInMillis()

                // get the categories
                val categories = childCategories.get(deviceUserEntry.id).waitForNonNullValue()
                val temporarilyAllowedApps = temporarilyAllowedApps.waitForNonNullValue()

                // get the current status
                val isScreenOn = appLogic.platformIntegration.isScreenOn()

                if (!isScreenOn) {
                    if (temporarilyAllowedApps.isNotEmpty()) {
                        resetTemporarilyAllowedApps()
                    }
                }

                val foregroundAppPackageName = appLogic.platformIntegration.getForegroundAppPackageName()
                // the following is not executed if the permission is missing

                if (foregroundAppPackageName == BuildConfig.APPLICATION_ID) {
                    // this app itself runs now -> no need for an status message
                    usedTimeUpdateHelper?.commit(appLogic)
                    appLogic.platformIntegration.setAppStatusMessage(null)
                } else if (foregroundAppPackageName != null && AndroidIntegrationApps.ignoredApps.contains(foregroundAppPackageName)) {
                    usedTimeUpdateHelper?.commit(appLogic)
                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                            appTitleCache.query(foregroundAppPackageName),
                            appLogic.context.getString(R.string.background_logic_whitelisted)
                    ))
                } else if (foregroundAppPackageName != null && temporarilyAllowedApps.contains(foregroundAppPackageName)) {
                    usedTimeUpdateHelper?.commit(appLogic)
                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                            appTitleCache.query(foregroundAppPackageName),
                            appLogic.context.getString(R.string.background_logic_temporarily_allowed)
                    ))
                } else if (foregroundAppPackageName != null) {
                    val appCategory = appCategories.get(Pair(foregroundAppPackageName, categories.map { it.id })).waitForNullableValue()
                    val category = categories.find { it.id == appCategory?.categoryId }
                            ?: categories.find { it.id == deviceUserEntry.categoryForNotAssignedApps }
                    val parentCategory = categories.find { it.id == category?.parentCategoryId }

                    if (category == null) {
                        usedTimeUpdateHelper?.commit(appLogic)

                        appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                title = appTitleCache.query(foregroundAppPackageName),
                                text = appLogic.context.getString(R.string.background_logic_opening_lockscreen)
                        ))
                        appLogic.platformIntegration.setSuspendedApps(listOf(foregroundAppPackageName), true)
                        appLogic.platformIntegration.showAppLockScreen(foregroundAppPackageName)
                    } else if (category.temporarilyBlocked or (parentCategory?.temporarilyBlocked == true)) {
                        usedTimeUpdateHelper?.commit(appLogic)

                        appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                title = appTitleCache.query(foregroundAppPackageName),
                                text = appLogic.context.getString(R.string.background_logic_opening_lockscreen)
                        ))
                        appLogic.platformIntegration.showAppLockScreen(foregroundAppPackageName)
                    } else {
                        val nowTimezone = TimeZone.getTimeZone(deviceUserEntry.timeZone)

                        val nowDate = DateInTimezone.newInstance(nowTimestamp, nowTimezone)
                        val minuteOfWeek = getMinuteOfWeek(nowTimestamp, nowTimezone)

                        // disable time limits temporarily feature
                        if (nowTimestamp < deviceUserEntry.disableLimitsUntil) {
                            appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                    title = appTitleCache.query(foregroundAppPackageName),
                                    text = appLogic.context.getString(R.string.background_logic_limits_disabled)
                            ))
                        } else if (
                        // check blocked time areas
                                (category.blockedMinutesInWeek.read(minuteOfWeek)) or
                                (parentCategory?.blockedMinutesInWeek?.read(minuteOfWeek) == true)
                        ) {
                            usedTimeUpdateHelper?.commit(appLogic)

                            appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                    title = appTitleCache.query(foregroundAppPackageName),
                                    text = appLogic.context.getString(R.string.background_logic_opening_lockscreen)
                            ))
                            appLogic.platformIntegration.showAppLockScreen(foregroundAppPackageName)
                        } else {
                            // check time limits
                            val rules = timeLimitRules.get(category.id).waitForNonNullValue()
                            val parentRules = parentCategory?.let {
                                timeLimitRules.get(it.id).waitForNonNullValue()
                            } ?: emptyList()

                            if (rules.isEmpty() and parentRules.isEmpty()) {
                                // unlimited
                                usedTimeUpdateHelper?.commit(appLogic)

                                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                        category.title + " - " + appTitleCache.query(foregroundAppPackageName),
                                        appLogic.context.getString(R.string.background_logic_no_timelimit)
                                ))
                            } else {
                                val usedTimes = usedTimesOfCategoryAndWeekByFirstDayOfWeek.get(Pair(category.id, nowDate.dayOfEpoch - nowDate.dayOfWeek)).waitForNonNullValue()
                                val parentUsedTimes = parentCategory?.let {
                                    usedTimesOfCategoryAndWeekByFirstDayOfWeek.get(Pair(it.id, nowDate.dayOfEpoch - nowDate.dayOfWeek)).waitForNonNullValue()
                                } ?: SparseArray()

                                val newUsedTimeItemBatchUpdateHelper = UsedTimeItemBatchUpdateHelper.eventuallyUpdateInstance(
                                        date = nowDate,
                                        childCategoryId = category.id,
                                        parentCategoryId = parentCategory?.id,
                                        oldInstance = usedTimeUpdateHelper,
                                        usedTimeItemForDayChild = usedTimes.get(nowDate.dayOfWeek),
                                        usedTimeItemForDayParent = parentUsedTimes.get(nowDate.dayOfWeek),
                                        logic = appLogic
                                )
                                usedTimeUpdateHelper = newUsedTimeItemBatchUpdateHelper

                                fun buildUsedTimesSparseArray(items: SparseArray<UsedTimeItem>, isParentCategory: Boolean): SparseLongArray {
                                    val result = SparseLongArray()

                                    for (i in 0..6) {
                                        val usedTimesItem = items[i]?.usedMillis

                                        if (newUsedTimeItemBatchUpdateHelper.date.dayOfWeek == i) {
                                            result.put(
                                                    i,
                                                    if (isParentCategory)
                                                        newUsedTimeItemBatchUpdateHelper.getTotalUsedTimeParent()
                                                    else
                                                        newUsedTimeItemBatchUpdateHelper.getTotalUsedTimeChild()
                                            )
                                        } else {
                                            result.put(i, usedTimesItem ?: 0)
                                        }
                                    }

                                    return result
                                }

                                val remainingChild = RemainingTime.getRemainingTime(
                                        nowDate.dayOfWeek,
                                        buildUsedTimesSparseArray(usedTimes, isParentCategory = false),
                                        rules,
                                        Math.max(0, category.extraTimeInMillis - newUsedTimeItemBatchUpdateHelper.getCachedExtraTimeToSubtract())
                                )

                                val remainingParent = parentCategory?.let {
                                    RemainingTime.getRemainingTime(
                                            nowDate.dayOfWeek,
                                            buildUsedTimesSparseArray(parentUsedTimes, isParentCategory = true),
                                            parentRules,
                                            Math.max(0, parentCategory.extraTimeInMillis - newUsedTimeItemBatchUpdateHelper.getCachedExtraTimeToSubtract())
                                    )
                                }

                                val remaining = RemainingTime.min(remainingChild, remainingParent)

                                if (remaining == null) {
                                    // unlimited

                                    usedTimeUpdateHelper?.commit(appLogic)

                                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                            category.title + " - " + appTitleCache.query(foregroundAppPackageName),
                                            appLogic.context.getString(R.string.background_logic_no_timelimit)
                                    ))
                                } else {
                                    // time limited
                                    if (remaining.includingExtraTime > 0) {
                                        if (remaining.default == 0L) {
                                            // using extra time

                                            appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                                    category.title + " - " + appTitleCache.query(foregroundAppPackageName),
                                                    appLogic.context.getString(R.string.background_logic_using_extra_time, TimeTextUtil.remaining(remaining.includingExtraTime.toInt(), appLogic.context))
                                            ))

                                            if (isScreenOn) {
                                                newUsedTimeItemBatchUpdateHelper.addUsedTime(
                                                        Math.min(previousMainLogicExecutionTime, MAX_USED_TIME_PER_ROUND),    // never save more than a second of used time
                                                        true,
                                                        appLogic
                                                )
                                            }
                                        } else {
                                            // using normal contingent

                                            appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                                    category.title + " - " + appTitleCache.query(foregroundAppPackageName),
                                                    TimeTextUtil.remaining(remaining.default.toInt(), appLogic.context)
                                            ))

                                            if (isScreenOn) {
                                                newUsedTimeItemBatchUpdateHelper.addUsedTime(
                                                        Math.min(previousMainLogicExecutionTime, MAX_USED_TIME_PER_ROUND),    // never save more than a second of used time
                                                        false,
                                                        appLogic
                                                )
                                            }
                                        }
                                    } else {
                                        // there is not time anymore

                                        newUsedTimeItemBatchUpdateHelper.commit(appLogic)

                                        appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                                                title = appTitleCache.query(foregroundAppPackageName),
                                                text = appLogic.context.getString(R.string.background_logic_opening_lockscreen)
                                        ))
                                        appLogic.platformIntegration.showAppLockScreen(foregroundAppPackageName)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                            appLogic.context.getString(R.string.background_logic_idle_title),
                            appLogic.context.getString(R.string.background_logic_idle_text)
                    ))
                }
            } catch (ex: SecurityException) {
                // this is handled by an other main loop (with a delay)

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_permission)
                ))
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "exception during running main loop", ex)
                }

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_internal)
                ))
            }

            liveDataCaches.reportLoopDone()

            // delay before running next time
            val endTime = appLogic.timeApi.getCurrentUptimeInMillis()
            previousMainLogicExecutionTime = (endTime - previousMainLoopEndTime).toInt()
            previousMainLoopEndTime = endTime

            val timeToWait = Math.max(10, BACKGROUND_SERVICE_INTERVAL - previousMainLogicExecutionTime)
            appLogic.timeApi.sleep(timeToWait)
        }
    }

    private suspend fun syncInstalledAppVersion() {
        val currentAppVersion = BuildConfig.VERSION_CODE
        val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

        if (deviceEntry != null) {
            if (deviceEntry.currentAppVersion != currentAppVersion) {
                ApplyActionUtil.applyAppLogicAction(
                        UpdateDeviceStatusAction(
                                newProtectionLevel = null,
                                newUsageStatsPermissionStatus = null,
                                newNotificationAccessPermission = null,
                                newAppVersion = currentAppVersion
                        ),
                        appLogic
                )
            }
        }
    }

    fun syncDeviceStatusAsync() {
        runAsync {
            syncDeviceStatus()
        }
    }

    private suspend fun syncDeviceStatusLoop() {
        while (true) {
            appLogic.deviceEntryIfEnabled.waitUntilValueMatches { it != null }

            syncDeviceStatus()

            appLogic.timeApi.sleep(CHECK_PERMISSION_INTERVAL)
        }
    }

    private val syncDeviceStatusLock = Mutex()

    private suspend fun syncDeviceStatus() {
        syncDeviceStatusLock.withLock {
            val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

            if (deviceEntry != null) {
                val protectionLevel = appLogic.platformIntegration.getCurrentProtectionLevel()
                val usageStatsPermission = appLogic.platformIntegration.getForegroundAppPermissionStatus()
                val notificationAccess = appLogic.platformIntegration.getNotificationAccessPermissionStatus()

                val emptyChanges = UpdateDeviceStatusAction(
                        newProtectionLevel = null,
                        newUsageStatsPermissionStatus = null,
                        newNotificationAccessPermission = null,
                        newAppVersion = null
                )

                var changes = emptyChanges

                if (protectionLevel != deviceEntry.currentProtectionLevel) {
                    changes = changes.copy(
                            newProtectionLevel = protectionLevel
                    )

                    if (protectionLevel == ProtectionLevel.DeviceOwner) {
                        appLogic.platformIntegration.setEnableSystemLockdown(true)
                    }
                }

                if (usageStatsPermission != deviceEntry.currentUsageStatsPermission) {
                    changes = changes.copy(
                            newUsageStatsPermissionStatus = usageStatsPermission
                    )
                }

                if (notificationAccess != deviceEntry.currentNotificationAccessPermission) {
                    changes = changes.copy(
                            newNotificationAccessPermission = notificationAccess
                    )
                }

                if (changes != emptyChanges) {
                    ApplyActionUtil.applyAppLogicAction(changes, appLogic)
                }
            }
        }
    }

    suspend fun resetTemporarilyAllowedApps() {
        Threads.database.executeAndWait(Runnable {
            appLogic.database.temporarilyAllowedApp().removeAllTemporarilyAllowedAppsSync()
        })
    }

    private suspend fun backupDatabaseLoop() {
        appLogic.timeApi.sleep(1000 * 60 * 5 /* 5 minutes */)

        while (true) {
            DatabaseBackup.with(appLogic.context).tryCreateDatabaseBackupAsync()

            appLogic.timeApi.sleep(1000 * 60 * 60 * 3 /* 3 hours */)
        }
    }
}
