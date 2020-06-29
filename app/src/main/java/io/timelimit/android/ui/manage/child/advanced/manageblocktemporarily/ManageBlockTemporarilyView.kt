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
package io.timelimit.android.ui.manage.child.advanced.manageblocktemporarily

import android.text.format.DateUtils
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel

object ManageBlockTemporarilyView {
    fun bind(
            userRelatedData: LiveData<UserRelatedData?>,
            lifecycleOwner: LifecycleOwner,
            container: LinearLayout,
            fragmentManager: FragmentManager,
            auth: ActivityViewModel,
            childId: String
    ) {
        val context = container.context
        val items = ManageBlockTemporarilyItems.build(
                userRelatedData = userRelatedData,
                timeApi = auth.logic.timeApi
        )

        items.observe(lifecycleOwner, Observer { categories ->

            container.removeAllViews()

            categories?.forEach {
                category ->

                val checkbox = CheckBox(context)

                checkbox.isChecked = category.checked
                checkbox.text = if (category.checked) {
                    if (category.endTime == 0L)
                        context.getString(R.string.manage_child_block_temporarily_no_end_time, category.categoryTitle)
                    else
                        context.getString(
                                R.string.manage_child_block_temporarily_until,
                                category.categoryTitle,
                                DateUtils.formatDateTime(
                                        context,
                                        category.endTime,
                                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                                                DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
                                )
                        )
                } else {
                    category.categoryTitle
                }

                checkbox.setOnLongClickListener {
                    if (auth.requestAuthenticationOrReturnTrue()) {
                        BlockTemporarilyDialogFragment.newInstance(
                                childId = childId,
                                categoryId = category.categoryId
                        ).show(fragmentManager)
                    }

                    true
                }

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != category.checked) {
                        if (!auth.tryDispatchParentAction(
                                        UpdateCategoryTemporarilyBlockedAction(
                                                categoryId = category.categoryId,
                                                blocked = !category.checked,
                                                endTime = null
                                        )
                                )) {
                            checkbox.isChecked = category.checked
                        }
                    }
                }

                container.addView(checkbox)
            }
        })
    }
}