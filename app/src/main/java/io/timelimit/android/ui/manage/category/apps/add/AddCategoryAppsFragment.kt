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
package io.timelimit.android.ui.manage.category.apps.add


import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentAddCategoryAppsBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.apps.addactivity.AddAppActivitiesDialogFragment
import io.timelimit.android.ui.view.AppFilterView

class AddCategoryAppsFragment : DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "x"
        private const val STATUS_PACKAGE_NAMES = "d"
        private const val STATUS_EDUCATED = "e"

        fun newInstance(params: ManageCategoryFragmentArgs) = AddCategoryAppsFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val database: Database by lazy { DefaultAppLogic.with(context!!).database }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val adapter = AddAppAdapter()
    private var didEducateAboutAddingAssignedApps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it == null || it.second.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        if (savedInstanceState != null) {
            adapter.selectedApps = savedInstanceState.getStringArrayList(STATUS_PACKAGE_NAMES)!!.toSet()
            didEducateAboutAddingAssignedApps = savedInstanceState.getBoolean(STATUS_EDUCATED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(STATUS_PACKAGE_NAMES, ArrayList(adapter.selectedApps))
        outState.putBoolean(STATUS_EDUCATED, didEducateAboutAddingAssignedApps)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentAddCategoryAppsBinding.inflate(LayoutInflater.from(context))
        val filter = AppFilterView.getFilterLive(binding.filter)

        val showAppsFromOtherCategories = MutableLiveData<Boolean>().apply { value = binding.showOtherCategoriesApps.isChecked }
        binding.showOtherCategoriesApps.setOnCheckedChangeListener { _, isChecked -> showAppsFromOtherCategories.value = isChecked }

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        val childCategories = database.category().getCategoriesByChildId(params.childId)
        val childCategoriesWithApps = childCategories.switchMap { categories ->
            database.categoryApp().getCategoryApps(categories.map { it.id })
                    .map { categoyApps -> categories to categoyApps }
        }

        val packageNamesAssignedToOtherCategories = childCategoriesWithApps.map { (_, apps) ->
            apps.filter { it.categoryId != params.categoryId }.map { it.packageName }.toSet()
        }.ignoreUnchanged()

        filter.switchMap { filter ->
            database.app().getApps()
                    .map{ apps -> apps.distinctBy { app -> app.packageName } }
                    .map { filter to it }
        }.map { (search, apps) ->
            apps.filter { search.matches(it) }
        }.switchMap { apps ->
            showAppsFromOtherCategories.switchMap { showOtherCategeories ->
                if (showOtherCategeories) {
                    liveDataFromValue(apps)
                } else {
                    packageNamesAssignedToOtherCategories.map { packagesFromOtherCategories ->
                        apps.filterNot { packagesFromOtherCategories.contains(it.packageName) }
                    }
                }
            }
        }.map { apps ->
            apps.sortedBy { app -> app.title.toLowerCase() }
        }.observe(this, Observer {
            val selectedPackageNames = adapter.selectedApps
            val visiblePackageNames = it.map { it.packageName }.toSet()
            val hiddenSelectedPackageNames = selectedPackageNames.toMutableSet().apply { removeAll(visiblePackageNames) }.size

            adapter.data = it
            binding.hiddenEntries = if (hiddenSelectedPackageNames == 0)
                null
            else
                resources.getQuantityString(R.plurals.category_apps_add_dialog_hidden_entries, hiddenSelectedPackageNames, hiddenSelectedPackageNames)
        })

        childCategoriesWithApps.map { (categories, apps) ->
            val categoryById = categories.associateBy { it.id }
            val categoryTitleByCategoryId = mutableMapOf<String, String>()

            apps.forEach {
                categoryTitleByCategoryId[it.packageName] = categoryById[it.categoryId]?.title ?: ""
            }

            categoryTitleByCategoryId
        }.observe(this, Observer {
            adapter.categoryTitleByPackageName = it
        })

        binding.addAppsButton.setOnClickListener {
            val packageNames = adapter.selectedApps.toList()

            if (packageNames.isNotEmpty()) {
                auth.tryDispatchParentAction(
                        AddCategoryAppsAction(
                                categoryId = params.categoryId,
                                packageNames = packageNames
                        )
                )
            }

            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.selectAllButton.setOnClickListener {
            adapter.selectedApps = adapter.selectedApps + (adapter.data?.map { it.packageName }?.toSet() ?: emptySet())
        }

        adapter.listener = object: AddAppAdapterListener {
            override fun onAppClicked(app: App) {
                if (adapter.selectedApps.contains(app.packageName)) {
                    adapter.selectedApps = adapter.selectedApps - setOf(app.packageName)
                } else {
                    if (!didEducateAboutAddingAssignedApps) {
                        if (adapter.categoryTitleByPackageName[app.packageName] != null) {
                            didEducateAboutAddingAssignedApps = true

                            AddAlreadyAssignedAppsInfoDialog().show(fragmentManager!!)
                        }
                    }

                    adapter.selectedApps = adapter.selectedApps + setOf(app.packageName)
                }
            }

            override fun onAppLongClicked(app: App): Boolean {
                return if (adapter.selectedApps.isEmpty()) {
                    AddAppActivitiesDialogFragment.newInstance(
                            childId = params.childId,
                            categoryId = params.categoryId,
                            packageName = app.packageName
                    ).show(fragmentManager!!)

                    dismissAllowingStateLoss()

                    true
                } else {
                    Toast.makeText(context, R.string.category_apps_add_dialog_cannot_add_activities_already_sth_selected, Toast.LENGTH_LONG).show()

                    false
                }
            }
        }

        return AlertDialog.Builder(context!!, R.style.AppTheme)
                .setView(binding.root)
                .create()
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}
