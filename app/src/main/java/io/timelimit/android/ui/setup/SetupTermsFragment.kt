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
package io.timelimit.android.ui.setup


import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.logic.DefaultAppLogic
import kotlinx.android.synthetic.main.fragment_setup_terms.*

class SetupTermsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_setup_terms, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btn_accept.setOnClickListener {
            acceptTerms()
        }

        btn_uninstall.setOnClickListener {
            uninstall()
        }

        terms_text.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun acceptTerms() {
        Navigation.findNavController(view!!).safeNavigate(
                SetupTermsFragmentDirections.actionSetupTermsFragmentToSetupDevicePermissionsFragment(),
                R.id.setupTermsFragment
        )
    }

    private fun uninstall() {
        DefaultAppLogic.with(context!!).platformIntegration.disableDeviceAdmin()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context!!.packageName}"))
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            startActivity(
                    Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${context!!.packageName}"))
            )
        }
    }
}
