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
package io.timelimit.android.ui.login

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.BlockingReasonUtil
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChildSignInAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class LoginDialogFragmentModel(application: Application): AndroidViewModel(application) {
    val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val logic = DefaultAppLogic.with(application)
    private val blockingReasonUtil = BlockingReasonUtil(logic)
    private val users = logic.database.user().getAllUsersLive()
    private val selectedUser = users.switchMap { users ->
        selectedUserId.map { userId ->
            users.find { it.id == userId }
        }
    }
    private val trustedTime = selectedUser.switchMap { blockingReasonUtil.getTrustedMinuteOfWeekLive(TimeZone.getTimeZone(it?.timeZone ?: "GMT")) }
    private val currentDeviceUser = logic.deviceUserId
    private val isCheckingPassword = MutableLiveData<Boolean>().apply { value = false }
    private val wasPasswordWrong = MutableLiveData<Boolean>().apply { value = false }
    private val isLoginDone = MutableLiveData<Boolean>().apply { value = false }
    private val loginLock = Mutex()

    val status: LiveData<LoginDialogStatus> = isLoginDone.switchMap { isLoginDone ->
        if (isLoginDone) {
            liveDataFromValue(LoginDialogDone as LoginDialogStatus)
        } else {
            selectedUser.switchMap { selectedUser ->
                when (selectedUser?.type) {
                    UserType.Parent -> {
                        val isAlreadyCurrentUser = currentDeviceUser.map { it == selectedUser.id }.ignoreUnchanged()
                        val loginScreen = isAlreadyCurrentUser.switchMap { isAlreadyCurrentUser ->
                            isCheckingPassword.switchMap { isCheckingPassword ->
                                wasPasswordWrong.map { wasPasswordWrong ->
                                    ParentUserLogin(
                                            isAlreadyCurrentDeviceUser = isAlreadyCurrentUser,
                                            isCheckingPassword = isCheckingPassword,
                                            wasPasswordWrong = wasPasswordWrong
                                    ) as LoginDialogStatus
                                }
                            }
                        }

                        if (selectedUser.blockedTimes.dataNotToModify.isEmpty) {
                            loginScreen
                        } else {
                            trustedTime.switchMap { time ->
                                if (selectedUser.blockedTimes.dataNotToModify[time]) {
                                    liveDataFromValue(ParentUserLoginBlockedTime as LoginDialogStatus)
                                } else {
                                    loginScreen
                                }
                            }
                        }
                    }
                    UserType.Child -> {
                        if (selectedUser.password.isEmpty()) {
                            liveDataFromValue(CanNotSignInChildHasNoPassword(childName = selectedUser.name) as LoginDialogStatus)
                        } else {
                            val isAlreadyCurrentUser = currentDeviceUser.map { it == selectedUser.id }.ignoreUnchanged()

                            isAlreadyCurrentUser.switchMap { isSignedIn ->
                                if (isSignedIn) {
                                    liveDataFromValue(ChildAlreadyDeviceUser as LoginDialogStatus)
                                } else {
                                    isCheckingPassword.switchMap { isCheckingPassword ->
                                        wasPasswordWrong.map { wasPasswordWrong ->
                                            ChildUserLogin(
                                                    isCheckingPassword = isCheckingPassword,
                                                    wasPasswordWrong = wasPasswordWrong
                                            ) as LoginDialogStatus
                                        }
                                    }
                                }
                            }
                        }
                    }
                    null -> {
                        users.map { users ->
                            UserListLoginDialogStatus(users) as LoginDialogStatus
                        }
                    }
                }
            }
        }
    }

    fun startSignIn(user: User) {
        selectedUserId.value = user.id
    }

    fun tryDefaultLogin(model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                logic.database.user().getParentUsersLive().waitForNonNullValue().singleOrNull()?.let { user ->
                    val emptyPasswordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync("", user.password) }

                    if (emptyPasswordValid) {
                        val isGoodTime = blockingReasonUtil.getTrustedMinuteOfWeekLive(TimeZone.getTimeZone(user.timeZone)).map { minuteOfWeek ->
                            user.blockedTimes.dataNotToModify[minuteOfWeek] == false
                        }.waitForNonNullValue()

                        if (isGoodTime) {
                            model.setAuthenticatedUser(AuthenticatedUser(
                                    userId = user.id,
                                    passwordHash = user.password
                            ))

                            isLoginDone.value = true
                        }
                    }
                }
            }
        }
    }

    fun tryParentLogin(
            password: String,
            model: ActivityViewModel
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntry = selectedUser.waitForNullableValue()
                    val ownDeviceId = logic.deviceId.waitForNullableValue()

                    if (userEntry?.type != UserType.Parent || ownDeviceId == null) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    val authenticatedUser = AuthenticatedUser(
                            userId = userEntry.id,
                            passwordHash = userEntry.password
                    )

                    model.setAuthenticatedUser(authenticatedUser)

                    isLoginDone.value = true
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun tryChildLogin(
            password: String,
            model: ActivityViewModel
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntry = selectedUser.waitForNullableValue()
                    val ownDeviceId = logic.deviceId.waitForNullableValue()

                    if (userEntry?.type != UserType.Child || ownDeviceId == null) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    ApplyActionUtil.applyChildAction(
                            action = ChildSignInAction,
                            database = logic.database,
                            childUserId = userEntry.id
                    )

                    Toast.makeText(getApplication(), R.string.login_child_done_toast, Toast.LENGTH_SHORT).show()

                    isLoginDone.value = true
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun resetPasswordWrong() {
        if (wasPasswordWrong.value == true) {
            wasPasswordWrong.value = false
        }
    }

    fun goBack(): Boolean {
        return if (status.value is UserListLoginDialogStatus) {
            false
        } else {
            selectedUserId.value = null

            true
        }
    }
}

sealed class LoginDialogStatus
data class UserListLoginDialogStatus(val usersToShow: List<User>): LoginDialogStatus()
object ParentUserLoginBlockedTime: LoginDialogStatus()
data class ParentUserLogin(
        val isAlreadyCurrentDeviceUser: Boolean,
        val isCheckingPassword: Boolean,
        val wasPasswordWrong: Boolean
): LoginDialogStatus()
object LoginDialogDone: LoginDialogStatus()
data class CanNotSignInChildHasNoPassword(val childName: String): LoginDialogStatus()
object ChildAlreadyDeviceUser: LoginDialogStatus()
data class ChildUserLogin(
        val isCheckingPassword: Boolean,
        val wasPasswordWrong: Boolean
): LoginDialogStatus()