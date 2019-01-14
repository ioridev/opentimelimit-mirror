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
package io.timelimit.android.ui.manage.child.advanced

import io.timelimit.android.databinding.DeleteChildViewBinding
import io.timelimit.android.sync.actions.RemoveUserAction
import io.timelimit.android.ui.main.ActivityViewModel

object DeleteChildView {
    fun bind(
            model: ActivityViewModel,
            view: DeleteChildViewBinding,
            childId: String
    ) {
        view.btnConfirmDeleteUser.isEnabled = view.checkConfirmDeleteUser.isChecked

        view.btnConfirmDeleteUser.setOnClickListener {
            model.tryDispatchParentAction(
                    RemoveUserAction(
                            userId = childId
                    )
            )
        }

        view.checkConfirmDeleteUser.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!model.requestAuthenticationOrReturnTrue()) {
                    view.checkConfirmDeleteUser.isChecked = false
                } else {
                    view.btnConfirmDeleteUser.isEnabled = true
                }
            } else {
                view.btnConfirmDeleteUser.isEnabled = false
            }
        }
    }
}
