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
package io.timelimit.android.ui.main

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class ActivityViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ActivityViewModel"
    }

    val logic = DefaultAppLogic.with(application)
    private val database = logic.database

    val shouldHighlightAuthenticationButton = MutableLiveData<Boolean>().apply { value = false }

    private val authenticatedUserMetadata = MutableLiveData<AuthenticatedUser?>().apply { value = null }

    val authenticatedUser = authenticatedUserMetadata.switchMap {
        authenticatedUser ->

        if (authenticatedUser == null) {
            liveDataFromValue(null as Pair<AuthenticatedUser, User>?)
        } else {
            database.user().getUserByIdLive(authenticatedUser.userId).map {
                if (it == null || it.password != authenticatedUser.passwordHash) {
                    null
                } else {
                    Pair(authenticatedUser, it)
                }
            }
        }
    }

    fun isParentAuthenticated(): Boolean {
        val user = authenticatedUser.value

        return user != null && user.second.type == UserType.Parent
    }

    fun requestAuthentication() {
        shouldHighlightAuthenticationButton.value = true
    }

    fun requestAuthenticationOrReturnTrue(): Boolean {
        if (isParentAuthenticated()) {
            return true
        } else {
            requestAuthentication()

            return false
        }
    }

    fun tryDispatchParentAction(action: ParentAction): Boolean = tryDispatchParentActions(listOf(action))

    fun tryDispatchParentActions(actions: List<ParentAction>): Boolean {
        val status = authenticatedUser.value

        if (status == null || status.second.type != UserType.Parent) {
            requestAuthentication()
            return false
        }

        runAsync {
            try {
                actions.forEach { action ->
                    ApplyActionUtil.applyParentAction(
                            action = action,
                            database = database,
                            platformIntegration = logic.platformIntegration
                    )
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "error dispatching actions", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
            }
        }

        return true
    }

    fun setAuthenticatedUser(user: AuthenticatedUser) {
        authenticatedUserMetadata.value = user
    }

    fun getAuthenticatedUser() = authenticatedUserMetadata.value

    fun logOut() {
        authenticatedUserMetadata.value = null
    }
}

data class AuthenticatedUser (
        val userId: String,
        val passwordHash: String
)
