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
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.DeleteCategoryAction
import io.timelimit.android.sync.actions.SetCategoryExtraTimeAction
import io.timelimit.android.sync.actions.UpdateCategoryTitleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import kotlinx.android.synthetic.main.fragment_category_settings.*

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
        return inflater.inflate(R.layout.fragment_category_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryEntry = appLogic.database.category().getCategoryByChildIdAndId(params.childId, params.categoryId)
        val categoryTitle = Transformations.map(categoryEntry) { it?.title }.ignoreUnchanged()

        categoryTitle.observe(this, Observer {
            if (it != null) {
                edit_category_title.setText(it)
            }
        })

        btn_delete_category.setOnClickListener { deleteCategory() }

        edit_category_title_go.setOnClickListener { doRenameCategory() }
        edit_category_title.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                doRenameCategory()

                true
            } else {
                false
            }
        }

        categoryEntry.observe(this, Observer {
            if (it != null) {
                val roundedCurrentTimeInMillis = (it.extraTimeInMillis / (1000 * 60)) * (1000 * 60)

                if (extra_time_selection.timeInMillis != roundedCurrentTimeInMillis) {
                    extra_time_selection.timeInMillis = roundedCurrentTimeInMillis
                }
            }
        })

        extra_time_btn_ok.setOnClickListener {
            val newExtraTime = extra_time_selection.timeInMillis

            if (
                    auth.tryDispatchParentAction(
                            SetCategoryExtraTimeAction(
                                    categoryId = params.categoryId,
                                    newExtraTime = newExtraTime
                            )
                    )
            ) {
                Snackbar.make(view, R.string.category_settings_extra_time_change_toast, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun doRenameCategory() {
        val newTitle = edit_category_title.text.toString()

        if (TextUtils.isEmpty(newTitle)) {
            Snackbar.make(edit_category_title, R.string.category_settings_rename_empty, Snackbar.LENGTH_SHORT).show()
            return
        }

        if (
                auth.tryDispatchParentAction(
                        UpdateCategoryTitleAction(
                                categoryId = params.categoryId,
                                newTitle = newTitle
                        )
                )
        ) {
            Snackbar.make(edit_category_title, R.string.category_settings_rename_success, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun deleteCategory() {
        if (auth.requestAuthenticationOrReturnTrue()) {
            DeleteCategoryDialogFragment.newInstance(params).show(fragmentManager!!)
        }
    }
}
