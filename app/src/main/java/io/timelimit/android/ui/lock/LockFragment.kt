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
package io.timelimit.android.ui.lock

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.TemporarilyAllowedApp
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.LockFragmentBinding
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.*
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.IncrementCategoryExtraTimeAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits.ManageDisableTimelimitsViewHelper
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.view.SelectTimeSpanViewListener

class LockFragment : Fragment() {
    companion object {
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_ACTIVITY = "activitiy"

        fun newInstance(packageName: String, activity: String?): LockFragment {
            val result = LockFragment()
            val arguments = Bundle()

            arguments.putString(EXTRA_PACKAGE_NAME, packageName)

            if (activity != null) {
                arguments.putString(EXTRA_ACTIVITY, activity)
            }

            result.arguments = arguments
            return result
        }
    }

    private val packageName: String by lazy { arguments!!.getString(EXTRA_PACKAGE_NAME)!! }
    private val activityName: String? by lazy {
        if (arguments!!.containsKey(EXTRA_ACTIVITY))
            arguments!!.getString(EXTRA_ACTIVITY)
        else
            null
    }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val title: String? by lazy { logic.platformIntegration.getLocalAppTitle(packageName) }
    private val blockingReason: LiveData<BlockingReasonDetail> by lazy { BlockingReasonUtil(logic).getBlockingReason(packageName, activityName) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = LockFragmentBinding.inflate(layoutInflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        val enableActivityLevelBlocking = logic.deviceEntry.map { it?.enableActivityLevelBlocking ?: false }

        binding.packageName = packageName

        enableActivityLevelBlocking.observe(this, Observer {
            binding.activityName = if (it) activityName?.removePrefix(packageName) else null
        })

        if (title != null) {
            binding.appTitle = title
        } else {
            binding.appTitle = "???"
        }

        binding.appIcon.setImageDrawable(logic.platformIntegration.getAppIcon(packageName))

        blockingReason.observe(this, Observer {
            when (it) {
                is NoBlockingReason -> activity!!.finish()
                is BlockedReasonDetails -> {
                    binding.reason = it.reason
                    binding.blockedKindLabel = when (it.level) {
                        BlockingLevel.Activity -> "Activity"
                        BlockingLevel.App -> "App"
                    }
                }
            }.let { /* require handling all cases */ }
        })

        val categories = logic.deviceUserEntry.switchMap {
            user ->

            if (user != null && user.type == UserType.Child) {
                Transformations.map(logic.database.category().getCategoriesByChildId(user.id)) {
                    categories ->

                    user to categories
                }
            } else {
                liveDataFromValue(null as Pair<User, List<Category>>?)
            }
        }.ignoreUnchanged()

        // bind category name of the app
        val appCategory = categories.switchMap {
            status ->

            if (status == null) {
                liveDataFromValue(null as Category?)
            } else {
                val (_, categoryItems) = status

                blockingReason.map { reason ->
                    if (reason is BlockedReasonDetails) {
                        reason.categoryId
                    } else {
                        null
                    }
                }.map { categoryId ->
                    categoryItems.find { it.id == categoryId }
                }
            }
        }

        appCategory.observe(this, Observer {
            binding.appCategoryTitle = it?.title
        })

        // bind add to category list
        categories.observe(this, Observer {
            status ->

            binding.addToCategoryOptions.removeAllViews()

            if (status == null) {
                // nothing to do
            } else {
                val (user, categoryEntries) = status

                categoryEntries.forEach {
                    category ->

                    val button = Button(context)

                    button.text = category.title
                    button.setOnClickListener {
                        _ ->

                        auth.tryDispatchParentAction(
                                AddCategoryAppsAction(
                                        categoryId = category.id,
                                        packageNames = listOf(packageName)
                                )
                        )
                    }

                    binding.addToCategoryOptions.addView(button)
                }

                run {
                    val button = Button(context)

                    button.text = getString(R.string.create_category_title)
                    button.setOnClickListener {
                        if (auth.requestAuthenticationOrReturnTrue()) {
                            CreateCategoryDialogFragment
                                    .newInstance(ManageChildFragmentArgs(childId = user.id))
                                    .show(fragmentManager!!)
                        }
                    }

                    binding.addToCategoryOptions.addView(button)
                }
            }
        })

        // bind adding extra time controls
        binding.extraTimeBtnOk.setOnClickListener {
            if (auth.isParentAuthenticated()) {
                runAsync {
                    val extraTimeToAdd = binding.extraTimeSelection.timeInMillis

                    if (extraTimeToAdd > 0) {
                        binding.extraTimeBtnOk.isEnabled = false

                        binding.extraTimeSelection.clearNumberPickerFocus()

                        val categoryId = appCategory.waitForNullableValue()?.id

                        if (categoryId != null) {
                            auth.tryDispatchParentAction(IncrementCategoryExtraTimeAction(
                                    categoryId = categoryId,
                                    addedExtraTime = extraTimeToAdd
                            ))
                        } else {
                            Snackbar.make(binding.root, R.string.error_general, Snackbar.LENGTH_SHORT).show()
                        }

                        binding.extraTimeBtnOk.isEnabled = true
                    }
                }
            } else {
                auth.requestAuthentication()
            }
        }

        logic.database.config().getEnableAlternativeDurationSelectionAsync().observe(this, Observer {
            binding.extraTimeSelection.enablePickerMode(it)
        })

        binding.extraTimeSelection.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                // ignore
            }

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    logic.database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        // bind disable time limits
        logic.deviceUserEntry.observe(this, Observer {
            child ->

            if (child != null) {
                binding.manageDisableTimeLimits.handlers = ManageDisableTimelimitsViewHelper.createHandlers(
                        childId = child.id,
                        childTimezone = child.timeZone,
                        activity = activity!!
                )

                binding.manageDisableTimeLimits.userName = child.name
            }
        })

        mergeLiveData(logic.deviceUserEntry, liveDataFromFunction { logic.timeApi.getCurrentTimeInMillis() }).map {
            (child, time) ->

            if (time == null || child == null) {
                null
            } else {
                ManageDisableTimelimitsViewHelper.getDisabledUntilString(child, time, context!!)
            }
        }.observe(this, Observer {
            binding.manageDisableTimeLimits.disableTimeLimitsUntilString = it
        })

        // bind disable temporarily blocking
        categories
                .map { it != null && it.second.filter { category -> category.temporarilyBlocked }.size > 1 }
                .observe(this, Observer {
                    binding.areMultipleCategoriesBlocked = it!!
                })

        binding.handlers = object: Handlers {
            override fun openMainApp() {
                startActivity(Intent(context, MainActivity::class.java))
            }

            override fun allowTemporarily() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val database = logic.database
                    val deviceIdLive = logic.deviceId

                    // this accesses the database directly because it is not synced
                    runAsync {
                        val deviceId = deviceIdLive.waitForNullableValue()

                        if (deviceId != null) {
                            logic.platformIntegration.setSuspendedApps(listOf(packageName), false)

                            Threads.database.executeAndWait(Runnable {
                                try {
                                    database.temporarilyAllowedApp().addTemporarilyAllowedAppSync(TemporarilyAllowedApp(
                                            packageName = packageName
                                    ))
                                } catch (ex: SQLiteConstraintException) {
                                    // ignore this
                                    //
                                    // this happens when touching that option more than once very fast
                                    // or if the device is under load
                                }
                            })
                        }
                    }
                }
            }

            override fun disableTemporarilyLockForAllCategories() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    runAsync {
                        categories.waitForNullableValue()?.second?.filter { it.temporarilyBlocked }?.map { it.id }?.forEach {
                            categoryId ->

                            auth.tryDispatchParentAction(
                                    UpdateCategoryTemporarilyBlockedAction(
                                            categoryId = categoryId,
                                            blocked = false
                                    )
                            )
                        }
                    }
                }
            }

            override fun disableTemporarilyLockForCurrentCategory() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    runAsync {
                        val category = appCategory.waitForNullableValue()

                        if (category != null) {
                            auth.tryDispatchParentAction(
                                    UpdateCategoryTemporarilyBlockedAction(
                                            categoryId = category.id,
                                            blocked = false
                                    )
                            )
                        }
                    }
                }
            }

            override fun showAuthenticationScreen() {
                (activity as LockActivity).showAuthenticationScreen()
            }
        }

        return binding.root
    }
}

interface Handlers {
    fun openMainApp()
    fun allowTemporarily()
    fun disableTemporarilyLockForCurrentCategory()
    fun disableTemporarilyLockForAllCategories()
    fun showAuthenticationScreen()
}
