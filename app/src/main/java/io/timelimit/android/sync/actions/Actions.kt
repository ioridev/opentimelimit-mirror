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
package io.timelimit.android.sync.actions

import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.sync.validation.ListValidation

// Tip: [Ctrl] + [A] and [Ctrl] + [Shift] + [Minus] make this file easy to read

/*
 * The actions describe things that happen.
 * The same actions (should) result in the same state if applied in the same order.
 * This actions are used for the remote control and monitoring.
 *
 */

// base types
sealed class Action

sealed class AppLogicAction: Action()
sealed class ParentAction: Action()

//
// now the concrete actions
//

data class AddUsedTimeAction(val categoryId: String, val dayOfEpoch: Int, val timeToAdd: Int, val extraTimeToSubtract: Int): AppLogicAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (dayOfEpoch < 0) {
            throw IllegalArgumentException()
        }

        if (timeToAdd < 0) {
            throw IllegalArgumentException()
        }

        if (extraTimeToSubtract < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class InstalledApp(val packageName: String, val title: String, val isLaunchable: Boolean, val recommendation: AppRecommendation)
data class AddInstalledAppsAction(val apps: List<InstalledApp>): AppLogicAction() {
    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(apps.map { it.packageName })
    }
}
data class RemoveInstalledAppsAction(val packageNames: List<String>): AppLogicAction() {
    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}

data class AddCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}
data class RemoveCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}

data class CreateCategoryAction(val childId: String, val categoryId: String, val title: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        IdGenerator.assertIdValid(childId)
    }
}
data class DeleteCategoryAction(val categoryId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}
data class UpdateCategoryTitleAction(val categoryId: String, val newTitle: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}
data class SetCategoryExtraTimeAction(val categoryId: String, val newExtraTime: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (newExtraTime < 0) {
            throw IllegalArgumentException("newExtraTime must be >= 0")
        }
    }
}
data class IncrementCategoryExtraTimeAction(val categoryId: String, val addedExtraTime: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (addedExtraTime <= 0) {
            throw IllegalArgumentException("addedExtraTime must be more than zero")
        }
    }
}
data class UpdateCategoryTemporarilyBlockedAction(val categoryId: String, val blocked: Boolean): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}

// DeviceDao

data class UpdateDeviceStatusAction(
        val newProtectionLevel: ProtectionLevel?,
        val newUsageStatsPermissionStatus: RuntimePermissionStatus?,
        val newNotificationAccessPermission: NewPermissionStatus?,
        val newAppVersion: Int?
): AppLogicAction() {
    init {
        if (newAppVersion != null && newAppVersion < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class IgnoreManipulationAction(
        val deviceId: String,
        val ignoreDeviceAdminManipulation: Boolean,
        val ignoreDeviceAdminManipulationAttempt: Boolean,
        val ignoreAppDowngrade: Boolean,
        val ignoreNotificationAccessManipulation: Boolean,
        val ignoreUsageStatsAccessManipulation: Boolean,
        val ignoreHadManipulation: Boolean
): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)
    }

    val isEmpty = (!ignoreDeviceAdminManipulation) &&
            (!ignoreDeviceAdminManipulationAttempt) &&
            (!ignoreAppDowngrade) &&
            (!ignoreNotificationAccessManipulation) &&
            (!ignoreUsageStatsAccessManipulation) &&
            (!ignoreHadManipulation)
}

object TriedDisablingDeviceAdminAction: AppLogicAction()

data class SetDeviceUserAction(val deviceId: String, val userId: String): ParentAction() {
    // user id can be an empty string
    init {
        IdGenerator.assertIdValid(deviceId)

        if (userId != "") {
            IdGenerator.assertIdValid(userId)
        }
    }
}

data class UpdateCategoryBlockedTimesAction(val categoryId: String, val blockedTimes: ImmutableBitmask): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}

data class CreateTimeLimitRuleAction(val rule: TimeLimitRule): ParentAction()

data class UpdateTimeLimitRuleAction(val ruleId: String, val dayMask: Byte, val maximumTimeInMillis: Int, val applyToExtraTimeUsage: Boolean): ParentAction() {
    init {
        IdGenerator.assertIdValid(ruleId)

        if (maximumTimeInMillis < 0) {
            throw IllegalArgumentException()
        }

        if (dayMask < 0 || dayMask > (1 or 2 or 4 or 8 or 16 or 32 or 64)) {
            throw IllegalArgumentException()
        }
    }
}

data class DeleteTimeLimitRuleAction(val ruleId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(ruleId)
    }
}

// UserDao
data class AddUserAction(val name: String, val userType: UserType, val password: String?, val userId: String, val timeZone: String): ParentAction() {
    init {
        if (userType == UserType.Parent) {
            password!!
        }

        IdGenerator.assertIdValid(userId)
    }
}

data class ChangeParentPasswordAction(
        val parentUserId: String,
        val newPassword: String
): ParentAction() {
    init {
        IdGenerator.assertIdValid(parentUserId)

        if (newPassword.isEmpty()) {
            throw IllegalArgumentException("missing required parameter")
        }
    }
}

data class RemoveUserAction(val userId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)
    }
}

data class SetUserDisableLimitsUntilAction(val childId: String, val timestamp: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(childId)

        if (timestamp < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class UpdateDeviceNameAction(val deviceId: String, val name: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)

        if (name.isBlank()) {
            throw IllegalArgumentException("new device name must not be blank")
        }
    }
}
