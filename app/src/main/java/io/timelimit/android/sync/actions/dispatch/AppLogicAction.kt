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
package io.timelimit.android.sync.actions.dispatch

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.integration.platform.NewPermissionStatusUtil
import io.timelimit.android.integration.platform.ProtectionLevelUtil
import io.timelimit.android.integration.platform.RuntimePermissionStatusUtil
import io.timelimit.android.logic.ManipulationLogic
import io.timelimit.android.sync.actions.*

object LocalDatabaseAppLogicActionDispatcher {
    fun dispatchAppLogicActionSync(action: AppLogicAction, deviceId: String, database: Database, manipulationLogic: ManipulationLogic) {
        DatabaseValidation.assertDeviceExists(database, deviceId)

        database.beginTransaction()

        try {
            when(action) {
                is AddUsedTimeAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)!!
                    val parentCategoryEntry = if (categoryEntry.parentCategoryId.isNotEmpty())
                        database.category().getCategoryByIdSync(categoryEntry.parentCategoryId)
                    else
                        null

                    fun handleAddUsedTime(categoryId: String) {
                        // try to update
                        val updatedRows = database.usedTimes().addUsedTime(
                                categoryId = categoryId,
                                timeToAdd = action.timeToAdd,
                                dayOfEpoch = action.dayOfEpoch
                        )

                        if (updatedRows == 0) {
                            // create new entry

                            database.usedTimes().insertUsedTime(UsedTimeItem(
                                    categoryId = categoryId,
                                    dayOfEpoch = action.dayOfEpoch,
                                    usedMillis = action.timeToAdd.toLong()
                            ))
                        }


                        if (action.extraTimeToSubtract != 0) {
                            database.category().subtractCategoryExtraTime(
                                    categoryId = categoryId,
                                    removedExtraTime = action.extraTimeToSubtract
                            )
                        }
                    }

                    handleAddUsedTime(categoryEntry.id)

                    if (parentCategoryEntry?.childId == categoryEntry.childId) {
                        handleAddUsedTime(parentCategoryEntry.id)
                    }

                    null
                }
                is AddInstalledAppsAction -> {
                    database.app().addAppsSync(
                            action.apps.map {
                                App(
                                        packageName = it.packageName,
                                        title = it.title,
                                        isLaunchable = it.isLaunchable,
                                        recommendation = it.recommendation
                                )
                            }
                    )
                }
                is RemoveInstalledAppsAction -> {
                    database.app().removeAppsByPackageNamesSync(
                            packageNames = action.packageNames
                    )
                }
                is UpdateDeviceStatusAction -> {
                    var device = database.device().getDeviceByIdSync(deviceId)!!

                    if (action.newProtectionLevel != null) {
                        if (device.currentProtectionLevel != action.newProtectionLevel) {
                            device = device.copy(
                                    currentProtectionLevel = action.newProtectionLevel
                            )

                            if (ProtectionLevelUtil.toInt(action.newProtectionLevel) > ProtectionLevelUtil.toInt(device.highestProtectionLevel)) {
                                device = device.copy(
                                        highestProtectionLevel = action.newProtectionLevel
                                )
                            }

                            if (device.currentProtectionLevel != device.highestProtectionLevel) {
                                device = device.copy(hadManipulation = true)
                            }
                        }
                    }

                    if (action.newUsageStatsPermissionStatus != null) {
                        if (device.currentUsageStatsPermission != action.newUsageStatsPermissionStatus) {
                            device = device.copy(
                                    currentUsageStatsPermission = action.newUsageStatsPermissionStatus
                            )

                            if (RuntimePermissionStatusUtil.toInt(action.newUsageStatsPermissionStatus) > RuntimePermissionStatusUtil.toInt(device.highestUsageStatsPermission)) {
                                device = device.copy(
                                        highestUsageStatsPermission = action.newUsageStatsPermissionStatus
                                )
                            }

                            if (device.currentUsageStatsPermission != device.highestUsageStatsPermission) {
                                device = device.copy(hadManipulation = true)
                            }
                        }
                    }

                    if (action.newNotificationAccessPermission != null) {
                        if (device.currentNotificationAccessPermission != action.newNotificationAccessPermission) {
                            device = device.copy(
                                    currentNotificationAccessPermission = action.newNotificationAccessPermission
                            )

                            if (NewPermissionStatusUtil.toInt(action.newNotificationAccessPermission) > NewPermissionStatusUtil.toInt(device.highestNotificationAccessPermission)) {
                                device = device.copy(
                                        highestNotificationAccessPermission = action.newNotificationAccessPermission
                                )
                            }

                            if (device.currentNotificationAccessPermission != device.highestNotificationAccessPermission) {
                                device = device.copy(hadManipulation = true)
                            }
                        }
                    }

                    if (action.newAppVersion != null) {
                        if (device.currentAppVersion != action.newAppVersion) {
                            device = device.copy(
                                    currentAppVersion = action.newAppVersion,
                                    highestAppVersion = Math.max(device.highestAppVersion, action.newAppVersion)
                            )

                            if (device.currentAppVersion != device.highestAppVersion) {
                                device = device.copy(hadManipulation = true)
                            }
                        }
                    }

                    if (action.didReboot && device.considerRebootManipulation) {
                        device = device.copy(
                                manipulationDidReboot = true
                        )
                    }

                    database.device().updateDeviceEntry(device)

                    if (device.hasActiveManipulationWarning) {
                        manipulationLogic.lockDeviceSync()
                    }

                    null
                }
                is TriedDisablingDeviceAdminAction -> {
                    database.device().updateDeviceEntry(
                            database.device().getDeviceByIdSync(
                                    database.config().getOwnDeviceIdSync()!!
                            )!!.copy(
                                    manipulationTriedDisablingDeviceAdmin = true
                            )
                    )

                    manipulationLogic.lockDeviceSync()

                    null
                }
            }.let {  }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
