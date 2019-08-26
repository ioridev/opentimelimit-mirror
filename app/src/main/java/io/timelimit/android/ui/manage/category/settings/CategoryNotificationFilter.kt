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
package io.timelimit.android.ui.manage.category.settings

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.Category
import io.timelimit.android.databinding.CategoryNotificationFilterBinding
import io.timelimit.android.sync.actions.UpdateCategoryBlockAllNotificationsAction
import io.timelimit.android.ui.main.ActivityViewModel

object CategoryNotificationFilter {
    fun bind(
            view: CategoryNotificationFilterBinding,
            auth: ActivityViewModel,
            categoryLive: LiveData<Category?>,
            lifecycleOwner: LifecycleOwner
    ) {
        categoryLive.observe(lifecycleOwner, Observer { category ->
            val shouldBeChecked = category?.blockAllNotifications ?: false

            view.checkbox.setOnCheckedChangeListener { _, _ ->  }
            view.checkbox.isChecked = shouldBeChecked
            view.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != shouldBeChecked) {
                    if (
                            category != null &&
                            auth.tryDispatchParentAction(
                                    UpdateCategoryBlockAllNotificationsAction(
                                            categoryId = category.id,
                                            blocked = isChecked
                                    )
                            )
                    ) {
                        // ok
                    } else {
                        view.checkbox.isChecked = shouldBeChecked
                    }
                }
            }
        })
    }
}