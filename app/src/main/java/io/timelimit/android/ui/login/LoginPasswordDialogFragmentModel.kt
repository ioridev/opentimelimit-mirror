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
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoginPasswordDialogFragmentModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }
    private val statusInternal = MutableLiveData<LoginPasswordDialogFragmentStatus>().apply{
        value = LoginPasswordDialogFragmentStatus.Idle
    }
    private val loginLock = Mutex()

    val status = statusInternal.castDown()

    fun tryLogin(userId: String, password: String, model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                statusInternal.value = LoginPasswordDialogFragmentStatus.Working

                val userEntry = logic.database.user().getUserByIdLive(userId).waitForNullableValue()

                if (userEntry == null) {
                    statusInternal.value = LoginPasswordDialogFragmentStatus.PermanentlyFailed

                    return@runAsync
                }

                val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                if (!passwordValid) {
                    statusInternal.value = LoginPasswordDialogFragmentStatus.PasswordWrong

                    return@runAsync
                }

                model.setAuthenticatedUser(AuthenticatedUser(
                        userId = userId,
                        passwordHash = userEntry.password
                ))

                statusInternal.value = LoginPasswordDialogFragmentStatus.Success
            }
        }
    }

    fun resetPasswordWrong() {
        if (this.status.value == LoginPasswordDialogFragmentStatus.PasswordWrong) {
            this.statusInternal.value = LoginPasswordDialogFragmentStatus.Idle
        }
    }
}

enum class LoginPasswordDialogFragmentStatus {
    Working,
    PasswordWrong,
    Idle,
    Success,
    PermanentlyFailed
}
