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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.FragmentCategorySettingsBinding
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SetCategoryExtraTimeAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.view.SelectTimeSpanViewListener

class CategorySettingsFragment : Fragment() {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): CategorySettingsFragment {
            val result = CategorySettingsFragment()
            result.arguments = params.toBundle()
            return result
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentCategorySettingsBinding.inflate(inflater, container, false)

        val categoryEntry = appLogic.database.category().getCategoryByChildIdAndId(params.childId, params.categoryId)

        ManageCategoryForUnassignedApps.bind(
                binding = binding.categoryForUnassignedApps,
                lifecycleOwner = this,
                categoryId = params.categoryId,
                childId = params.childId,
                database = appLogic.database,
                auth = auth
        )

        ParentCategoryView.bind(
                binding = binding.parentCategory,
                lifecycleOwner = this,
                categoryId = params.categoryId,
                childId = params.childId,
                database = appLogic.database,
                fragmentManager = fragmentManager!!,
                auth = auth
        )

        CategoryNotificationFilter.bind(
                view = binding.notificationFilter,
                lifecycleOwner = this,
                auth = auth,
                categoryLive = categoryEntry
        )

        CategoryTimeWarningView.bind(
                view = binding.timeWarnings,
                auth = auth,
                categoryLive = categoryEntry,
                lifecycleOwner = this
        )

        binding.btnDeleteCategory.setOnClickListener { deleteCategory() }
        binding.editCategoryTitleGo.setOnClickListener { renameCategory() }

        categoryEntry.observe(this, Observer {
            if (it != null) {
                val roundedCurrentTimeInMillis = (it.extraTimeInMillis / (1000 * 60)) * (1000 * 60)

                if (binding.extraTimeSelection.timeInMillis != roundedCurrentTimeInMillis) {
                    binding.extraTimeSelection.timeInMillis = roundedCurrentTimeInMillis
                }
            }
        })

        binding.extraTimeBtnOk.setOnClickListener {
            binding.extraTimeSelection.clearNumberPickerFocus()

            val newExtraTime = binding.extraTimeSelection.timeInMillis

            if (
                    auth.tryDispatchParentAction(
                            SetCategoryExtraTimeAction(
                                    categoryId = params.categoryId,
                                    newExtraTime = newExtraTime
                            )
                    )
            ) {
                Snackbar.make(binding.root, R.string.category_settings_extra_time_change_toast, Snackbar.LENGTH_SHORT).show()
            }
        }

        appLogic.database.config().getEnableAlternativeDurationSelectionAsync().observe(this, Observer {
            binding.extraTimeSelection.enablePickerMode(it)
        })

        binding.extraTimeSelection.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                // ignore
            }

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    appLogic.database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        return binding.root
    }

    private fun renameCategory() {
        if (auth.requestAuthenticationOrReturnTrue()) {
            RenameCategoryDialogFragment.newInstance(params).show(fragmentManager!!)
        }
    }

    private fun deleteCategory() {
        if (auth.requestAuthenticationOrReturnTrue()) {
            DeleteCategoryDialogFragment.newInstance(params).show(fragmentManager!!)
        }
    }
}
