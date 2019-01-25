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
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.sync.actions.*
import java.util.*

object LocalDatabaseParentActionDispatcher {
    fun dispatchParentActionSync(action: ParentAction, database: Database) {
        database.beginTransaction()

        try {
            when (action) {
                is AddCategoryAppsAction -> {
                    // validate that the category exists
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    // remove same apps from other categories of the same child
                    val allCategoriesOfChild = database.category().getCategoriesByChildIdSync(categoryEntry.childId)

                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = allCategoriesOfChild.map { it.id }
                    )

                    // add the apps to the new category
                    database.categoryApp().addCategoryAppsSync(
                            action.packageNames.map {
                                CategoryApp(
                                        categoryId = action.categoryId,
                                        packageName = it
                                )
                            }
                    )
                }
                is RemoveCategoryAppsAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // remove the apps from the category
                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = listOf(action.categoryId)
                    )
                }
                is CreateCategoryAction -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    // create the category
                    database.category().addCategory(Category(
                            id = action.categoryId,
                            childId = action.childId,
                            title = action.title,
                            // nothing blocked by default
                            blockedMinutesInWeek = ImmutableBitmask(BitSet()),
                            extraTimeInMillis = 0,
                            temporarilyBlocked = false
                    ))
                }
                is DeleteCategoryAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // delete all related data and the category
                    database.timeLimitRules().deleteTimeLimitRulesByCategory(action.categoryId)
                    database.usedTimes().deleteUsedTimeItems(action.categoryId)
                    database.categoryApp().deleteCategoryAppsByCategoryId(action.categoryId)
                    database.category().deleteCategory(action.categoryId)
                }
                is UpdateCategoryTitleAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTitle(
                            categoryId = action.categoryId,
                            newTitle = action.newTitle
                    )
                }
                is SetCategoryExtraTimeAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    if (action.newExtraTime < 0) {
                        throw IllegalArgumentException("invalid new extra time")
                    }

                    database.category().updateCategoryExtraTime(action.categoryId, action.newExtraTime)
                }
                is IncrementCategoryExtraTimeAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    if (action.addedExtraTime < 0) {
                        throw IllegalArgumentException("invalid added extra time")
                    }

                    database.category().incrementCategoryExtraTime(action.categoryId, action.addedExtraTime)
                }
                is UpdateCategoryTemporarilyBlockedAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTemporarilyBlocked(action.categoryId, action.blocked)
                }
                is DeleteTimeLimitRuleAction -> {
                    DatabaseValidation.assertTimelimitRuleExists(database, action.ruleId)

                    database.timeLimitRules().deleteTimeLimitRuleByIdSync(action.ruleId)
                }
                is AddUserAction -> {
                    database.user().addUserSync(User(
                            id = action.userId,
                            name = action.name,
                            type = action.userType,
                            timeZone = action.timeZone,
                            password = if (action.password == null) "" else action.password,
                            disableLimitsUntil = 0,
                            categoryForNotAssignedApps = ""
                    ))
                }
                is UpdateCategoryBlockedTimesAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryBlockedTimes(action.categoryId, action.blockedTimes)
                }
                is CreateTimeLimitRuleAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.rule.categoryId)

                    database.timeLimitRules().addTimeLimitRule(action.rule)
                }
                is UpdateTimeLimitRuleAction -> {
                    val oldRule = database.timeLimitRules().getTimeLimitRuleByIdSync(action.ruleId)!!

                    database.timeLimitRules().updateTimeLimitRule(oldRule.copy(
                            maximumTimeInMillis = action.maximumTimeInMillis,
                            dayMask = action.dayMask,
                            applyToExtraTimeUsage = action.applyToExtraTimeUsage
                    ))
                }
                is SetDeviceUserAction -> {
                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    if (action.userId != "") {
                        DatabaseValidation.assertUserExists(database, action.userId)
                    }

                    database.device().updateDeviceUser(
                            deviceId = action.deviceId,
                            userId = action.userId
                    )
                }
                is SetUserDisableLimitsUntilAction -> {
                    val affectedRows = database.user().updateDisableChildUserLimitsUntil(
                            childId = action.childId,
                            timestamp = action.timestamp
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided user id does not exist")
                    }

                    null
                }
                is UpdateDeviceNameAction -> {
                    val affectedRows = database.device().updateDeviceName(
                            deviceId = action.deviceId,
                            name = action.name
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided device id was invalid")
                    }

                    null
                }
                is RemoveUserAction -> {
                    // authentication is not checked locally, only at the server

                    val userToDelete = database.user().getUserByIdSync(action.userId)!!

                    if (userToDelete.type == UserType.Parent) {
                        val currentParents = database.user().getParentUsersSync()

                        if (currentParents.size <= 1) {
                            throw IllegalStateException("would delete last parent")
                        }
                    }

                    if (userToDelete.type == UserType.Child) {
                        val categories = database.category().getCategoriesByChildIdSync(userToDelete.id)

                        categories.forEach {
                            category ->

                            dispatchParentActionSync(
                                    DeleteCategoryAction(
                                            categoryId = category.id
                                    ),
                                    database
                            )
                        }
                    }

                    database.device().unassignCurrentUserFromAllDevices(action.userId)

                    database.user().deleteUsersByIds(listOf(action.userId))
                }
                is ChangeParentPasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentUserId)

                    if (userEntry == null || userEntry.type != UserType.Parent) {
                        throw IllegalArgumentException("invalid user entry")
                    }

                    // the client does not have the data to check the integrity

                    database.user().updateUserSync(
                            userEntry.copy(
                                    password = action.newPassword
                            )
                    )
                }
                is IgnoreManipulationAction -> {
                    val originalDeviceEntry = database.device().getDeviceByIdSync(action.deviceId)!!
                    var deviceEntry = originalDeviceEntry

                    if (action.ignoreDeviceAdminManipulation) {
                        deviceEntry = deviceEntry.copy(highestProtectionLevel = deviceEntry.currentProtectionLevel)
                    }

                    if (action.ignoreDeviceAdminManipulationAttempt) {
                        deviceEntry = deviceEntry.copy(manipulationTriedDisablingDeviceAdmin = false)
                    }

                    if (action.ignoreAppDowngrade) {
                        deviceEntry = deviceEntry.copy(highestAppVersion = deviceEntry.currentAppVersion)
                    }

                    if (action.ignoreNotificationAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestNotificationAccessPermission = deviceEntry.currentNotificationAccessPermission)
                    }

                    if (action.ignoreUsageStatsAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestUsageStatsPermission = deviceEntry.currentUsageStatsPermission)
                    }

                    if (action.ignoreHadManipulation) {
                        deviceEntry = deviceEntry.copy(hadManipulation = false)
                    }

                    database.device().updateDeviceEntry(deviceEntry)
                }
                is SetCategoryForUnassignedApps -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    if (action.categoryId.isNotEmpty()) {
                        val category = database.category().getCategoryByIdSync(action.categoryId)!!

                        if (category.childId != action.childId) {
                            throw IllegalArgumentException("category does not belong to child")
                        }
                    }

                    database.user().updateCategoryForUnassignedApps(
                            categoryId = action.categoryId,
                            childId = action.childId
                    )
                }
            }.let { }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
