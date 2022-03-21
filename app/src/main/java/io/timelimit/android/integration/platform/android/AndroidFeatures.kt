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
package io.timelimit.android.integration.platform.android

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import io.timelimit.android.R
import io.timelimit.android.integration.platform.PlatformFeature

object AndroidFeatures {
    private const val FEATURE_ADB = "adb"

    fun applyBlockedFeatures(features: Set<String>, policyManager: DevicePolicyManager, admin: ComponentName): Boolean {
        if (features.contains(FEATURE_ADB)) policyManager.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        else policyManager.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)

        return true
    }

    fun getFeaturesAssumingDeviceOwnerGranted(context: Context): List<PlatformFeature> {
        val result = mutableListOf<PlatformFeature>()

        result.add(PlatformFeature(
            id = FEATURE_ADB,
            title = context.getString(R.string.dummy_app_feature_adb)
        ))

        return result
    }
}