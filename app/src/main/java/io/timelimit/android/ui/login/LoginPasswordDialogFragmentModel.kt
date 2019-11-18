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
package io.timelimit.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.BlockingReasonUtil
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

class LoginPasswordDialogFragmentModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic = DefaultAppLogic.with(application)
    private val blockingReasonUtil = BlockingReasonUtil(logic)
    val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val selectedUser = selectedUserId.switchMap { userId ->
        if (userId != null) {
            logic.database.user().getParentUserByIdLive(userId)
        } else {
            liveDataFromValue(null as User?)
        }
    }
    private val loginLock = Mutex()
    private val isValidating = MutableLiveData<Boolean>().apply { value = false }
    private val wasPasswordWrong = MutableLiveData<Boolean>().apply { value = false }
    private val hadSuccess = MutableLiveData<Boolean>().apply { value = false }

    val status = hadSuccess.switchMap { hadSuccess ->
        if (hadSuccess) {
            liveDataFromValue(SuccessLoginDialogStatus as LoginDialogStatus)
        } else {
            selectedUser.switchMap { selectedUser ->
                if (selectedUser == null) {
                    liveDataFromValue(UserListLoginDialogStatus as LoginDialogStatus)
                } else {
                    val isGoodTime = blockingReasonUtil.getTrustedMinuteOfWeekLive(TimeZone.getTimeZone(selectedUser.timeZone)).map { minuteOfWeek ->
                        selectedUser.blockedTimes.dataNotToModify[minuteOfWeek] == false
                    }.ignoreUnchanged()

                    isGoodTime.switchMap { goodTime ->
                        if (goodTime) {
                            isValidating.switchMap { isValidating ->
                                if (isValidating) {
                                    liveDataFromValue(ValidationRunningLoginDialogStatus as LoginDialogStatus)
                                } else {
                                    wasPasswordWrong.map { wasPasswordWrong ->
                                        if (wasPasswordWrong) {
                                            WrongPasswordLoginDialogStatus
                                        } else {
                                            WaitingForPasswordLoginDialogStatus
                                        }
                                    }
                                }
                            }
                        } else {
                            liveDataFromValue(WrongTimeLoginDialogStatus as LoginDialogStatus)
                        }
                    }
                }
            }
        }
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

                            hadSuccess.value = true
                        }
                    }
                }
            }
        }
    }

    fun tryLogin(password: String, model: ActivityViewModel) {
        isValidating.value = true

        runAsync {
            try {
                loginLock.withLock {
                    val userEntry = selectedUser.waitForNullableValue() ?: return@runAsync

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    model.setAuthenticatedUser(AuthenticatedUser(
                            userId = userEntry.id,
                            passwordHash = userEntry.password
                    ))

                    hadSuccess.value = true
                }
            } finally {
                isValidating.value = false
            }
        }
    }

    fun resetPasswordWrong() {
        wasPasswordWrong.value = false
    }
}

sealed class LoginDialogStatus
object UserListLoginDialogStatus: LoginDialogStatus()
object WrongTimeLoginDialogStatus: LoginDialogStatus()
object WaitingForPasswordLoginDialogStatus: LoginDialogStatus()
object ValidationRunningLoginDialogStatus: LoginDialogStatus()
object WrongPasswordLoginDialogStatus: LoginDialogStatus()
object SuccessLoginDialogStatus: LoginDialogStatus()