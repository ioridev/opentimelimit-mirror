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
package io.timelimit.android.ui.lock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class LockActivity : AppCompatActivity(), ActivityViewModelHolder {
    companion object {
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val LOGIN_DIALOG_TAG = "loginDialog"

        fun start(context: Context, packageName: String) {
            context.startActivity(
                    Intent(context, LockActivity::class.java)
                            .putExtra(EXTRA_PACKAGE_NAME, packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    val blockedPackageName: String by lazy {
        intent.getStringExtra(EXTRA_PACKAGE_NAME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lock_activity)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, LockFragment.newInstance(blockedPackageName))
                    .commitNow()
        }
    }

    override fun getActivityViewModel(): ActivityViewModel {
        return ViewModelProviders.of(this).get(ActivityViewModel::class.java)
    }

    override fun showAuthenticationScreen() {
        NewLoginFragment().showSafe(supportFragmentManager, LOGIN_DIALOG_TAG)
    }

    override fun onResume() {
        super.onResume()

        lockTaskModeWorkaround()
    }

    override fun onPause() {
        super.onPause()

        lockTaskModeWorkaround()
    }

    override fun onStop() {
        super.onStop()

        if (!isChangingConfigurations) {
            getActivityViewModel().logOut()
        }
    }

    fun lockTaskModeWorkaround() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val platformIntegration = DefaultAppLogic.with(this).platformIntegration
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
            else
                activityManager.isInLockTaskMode

            if (isLocked) {
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), true)
                platformIntegration.setSuspendedApps(listOf(blockedPackageName), false)
            }
        }
    }
}
