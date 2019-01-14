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
package io.timelimit.android.sync.actions.apply

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.data.transaction
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.ManipulationLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseAppLogicActionDispatcher
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseParentActionDispatcher

object ApplyActionUtil {
    suspend fun applyAppLogicAction(action: AppLogicAction, appLogic: AppLogic) {
        applyAppLogicAction(action, appLogic.database, appLogic.manipulationLogic)
    }

    private suspend fun applyAppLogicAction(action: AppLogicAction, database: Database, manipulationLogic: ManipulationLogic) {
        Threads.database.executeAndWait {
            database.transaction().use {
                LocalDatabaseAppLogicActionDispatcher.dispatchAppLogicActionSync(action, database.config().getOwnDeviceIdSync()!!, database, manipulationLogic)

                database.setTransactionSuccessful()
            }
        }
    }

    suspend fun applyParentAction(action: ParentAction, database: Database, platformIntegration: PlatformIntegration) {
        Threads.database.executeAndWait {
            database.transaction().use {
                LocalDatabaseParentActionDispatcher.dispatchParentActionSync(action, database)

                // disable suspending the assigned app
                if (action is AddCategoryAppsAction) {
                    val thisDeviceId = database.config().getOwnDeviceIdSync()!!
                    val thisDeviceEntry = database.device().getDeviceByIdSync(thisDeviceId)!!

                    if (thisDeviceEntry.currentUserId != "") {
                        val userCategories = database.category().getCategoriesByChildIdSync(thisDeviceEntry.currentUserId)

                        if (userCategories.find { category -> category.id == action.categoryId } != null) {
                            platformIntegration.setSuspendedApps(action.packageNames, false)
                        }
                    }
                }

                if (action is SetDeviceUserAction) {
                    val thisDeviceId = database.config().getOwnDeviceIdSync()!!

                    if (action.deviceId == thisDeviceId) {
                        platformIntegration.stopSuspendingForAllApps()
                    }
                }

                database.setTransactionSuccessful()
            }
        }
    }
}
