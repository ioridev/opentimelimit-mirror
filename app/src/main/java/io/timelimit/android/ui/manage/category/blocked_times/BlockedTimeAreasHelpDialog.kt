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
package io.timelimit.android.ui.manage.category.blocked_times


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import kotlinx.android.synthetic.main.fragment_blocked_time_areas_help_dialog.*

class BlockedTimeAreasHelpDialog : BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "BlockedTimeAreasHelpDialog"
        private const val FOR_USER = "forUser"

        fun newInstance(forUser: Boolean) = BlockedTimeAreasHelpDialog().apply {
            arguments = Bundle().apply {
                putBoolean(FOR_USER, forUser)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_blocked_time_areas_help_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val forUser = arguments?.getBoolean(FOR_USER, false)

        if (forUser == true) {
            text1.setText(R.string.manage_parent_blocked_times_description)
        }
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}
