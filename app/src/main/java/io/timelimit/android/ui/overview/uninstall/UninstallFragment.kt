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
package io.timelimit.android.ui.overview.uninstall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.databinding.FragmentUninstallBinding
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel

class UninstallFragment : Fragment() {
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentUninstallBinding.inflate(inflater, container, false)

        binding.uninstall.isEnabled = binding.checkConfirm.isChecked
        binding.checkConfirm.setOnCheckedChangeListener { _, isChecked -> binding.uninstall.isEnabled = isChecked }

        binding.uninstall.setOnClickListener { reset(revokePermissions = binding.checkPermissions.isChecked) }

        return binding.root
    }

    private fun reset(revokePermissions: Boolean) {
        if (auth.requestAuthenticationOrReturnTrue()) {
            logic.platformIntegration.setEnableSystemLockdown(false)

            if (revokePermissions) {
                logic.platformIntegration.disableDeviceAdmin()
            }

            runAsync {
                logic.appSetupLogic.dangerousResetApp()
            }
        }
    }
}
