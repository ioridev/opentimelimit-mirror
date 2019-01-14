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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.FragmentManageChildAdvancedBinding
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits.ManageDisableTimelimitsViewHelper

class ManageChildAdvancedFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "ManageChildAdvanced"

        fun newInstance(params: ManageChildFragmentArgs) = ManageChildAdvancedFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(arguments!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val childEntry: LiveData<User?> by lazy { logic.database.user().getChildUserByIdLive(params.childId) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentManageChildAdvancedBinding.inflate(layoutInflater, container, false)

        run {
            // blocked categories

            val categoriesLive = logic.database.category().getCategoriesByChildId(params.childId)

            categoriesLive.observe(this, Observer {
                categories ->

                binding.blockedCategoriesCheckboxContainer.removeAllViews()

                categories?.forEach {
                    category ->

                    val checkbox = CheckBox(context)

                    checkbox.isChecked = category.temporarilyBlocked
                    checkbox.text = category.title

                    checkbox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked != category.temporarilyBlocked) {
                            if (!auth.tryDispatchParentAction(
                                            UpdateCategoryTemporarilyBlockedAction(
                                                    categoryId = category.id,
                                                    blocked = !category.temporarilyBlocked
                                            )
                                    )) {
                                checkbox.isChecked = category.temporarilyBlocked
                            }
                        }
                    }

                    binding.blockedCategoriesCheckboxContainer.addView(checkbox)
                }
            })
        }

        run {
            // disable time limits

            childEntry.observe(this, Observer {
                child ->

                if (child != null) {
                    binding.disableTimeLimits.handlers = ManageDisableTimelimitsViewHelper.createHandlers(
                            childId = params.childId,
                            childTimezone = child.timeZone,
                            activity = activity!!
                    )

                    binding.disableTimeLimits.userName = child.name
                }
            })

            mergeLiveData(childEntry, liveDataFromFunction { logic.timeApi.getCurrentTimeInMillis() }).map {
                (child, time) ->

                if (time == null || child == null) {
                    null
                } else {
                    ManageDisableTimelimitsViewHelper.getDisabledUntilString(child, time, context!!)
                }
            }.observe(this, Observer {
                binding.disableTimeLimits.disableTimeLimitsUntilString = it
            })
        }

        binding.deleteUserButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                DeleteChildDialogFragment.newInstance(params.childId).show(fragmentManager!!)
            }
        }

        return binding.root
    }
}
