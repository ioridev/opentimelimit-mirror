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
package io.timelimit.android.ui.lock

import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.*
import io.timelimit.android.data.model.derived.DeviceAndUserRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.databinding.LockFragmentBinding
import io.timelimit.android.databinding.LockFragmentCategoryButtonBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.*
import io.timelimit.android.logic.blockingreason.AppBaseHandling
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.IncrementCategoryExtraTimeAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits.ManageDisableTimelimitsViewHelper
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import java.util.*

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
    private val deviceAndUserRelatedData: LiveData<DeviceAndUserRelatedData?> by lazy {
        logic.database.derivedDataDao().getUserAndDeviceRelatedDataLive()
    }
    private val batteryStatus: LiveData<BatteryStatus> by lazy {
        logic.platformIntegration.getBatteryStatusLive()
    }
    private lateinit var binding: LockFragmentBinding
    private val handlingCache = CategoryHandlingCache()
    private val timeModificationListener: () -> Unit = { update() }

    private val updateRunnable = Runnable { update() }

    fun scheduleUpdate(delay: Long) {
        logic.timeApi.cancelScheduledAction(updateRunnable)
        logic.timeApi.runDelayedByUptime(updateRunnable, delay)
    }

    fun unscheduleUpdate() {
        logic.timeApi.cancelScheduledAction(updateRunnable)
    }

    private fun update() {
        val deviceAndUserRelatedData = deviceAndUserRelatedData.value ?: return
        val batteryStatus = batteryStatus.value ?: return
        val now = logic.timeApi.getCurrentTimeInMillis()

        if (deviceAndUserRelatedData.userRelatedData?.user?.type != UserType.Child) {
            binding.reason = BlockingReason.None
            binding.handlers = null
            activity?.finish()
            return
        }

        handlingCache.reportStatus(
                user = deviceAndUserRelatedData.userRelatedData,
                batteryStatus = batteryStatus,
                timeInMillis = now
        )

        val appBaseHandling = AppBaseHandling.calculate(
                foregroundAppPackageName = packageName,
                foregroundAppActivityName = activityName,
                deviceRelatedData = deviceAndUserRelatedData.deviceRelatedData,
                userRelatedData = deviceAndUserRelatedData.userRelatedData,
                pauseForegroundAppBackgroundLoop = false,
                pauseCounting = false
        )

        binding.activityName = if (deviceAndUserRelatedData.deviceRelatedData.deviceEntry.enableActivityLevelBlocking)
            activityName?.removePrefix(packageName)
        else
            null

        if (appBaseHandling is AppBaseHandling.UseCategories) {
            val categoryHandlings = appBaseHandling.categoryIds.map { handlingCache.get(it) }
            val blockingHandling = categoryHandlings.find { it.shouldBlockActivities }

            if (blockingHandling == null) {
                binding.reason = BlockingReason.None
                binding.handlers = null
                activity?.finish()
                return
            }

            binding.appCategoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title
            binding.reason = blockingHandling.activityBlockingReason
            binding.blockedKindLabel = when (appBaseHandling.level) {
                BlockingLevel.Activity -> "Activity"
                BlockingLevel.App -> "App"
            }
            setupHandlers(
                    blockedCategoryId = blockingHandling.createdWithCategoryRelatedData.category.id,
                    userRelatedData = deviceAndUserRelatedData.userRelatedData
            )
            bindExtraTimeView(
                    categoryId = blockingHandling.createdWithCategoryRelatedData.category.id,
                    timeZone = deviceAndUserRelatedData.userRelatedData.timeZone
            )
            binding.manageDisableTimeLimits.handlers = ManageDisableTimelimitsViewHelper.createHandlers(
                    childId = deviceAndUserRelatedData.userRelatedData.user.id,
                    childTimezone = deviceAndUserRelatedData.userRelatedData.user.timeZone,
                    activity = activity!!
            )

            scheduleUpdate((blockingHandling.dependsOnMaxTime - now))
        } else if (appBaseHandling is AppBaseHandling.BlockDueToNoCategory) {
            binding.appCategoryTitle = null
            binding.reason = BlockingReason.NotPartOfAnCategory
            binding.blockedKindLabel = "App"
            setupHandlers(
                    blockedCategoryId = null,
                    userRelatedData = deviceAndUserRelatedData.userRelatedData
            )

            bindAddToCategoryOptions(deviceAndUserRelatedData.userRelatedData)
        } else {
            binding.reason = BlockingReason.None
            binding.handlers = null
            activity?.finish()
            return
        }
    }

    private fun setupHandlers(userRelatedData: UserRelatedData, blockedCategoryId: String?) {
        binding.handlers = object: Handlers {
            override fun openMainApp() {
                startActivity(Intent(context, MainActivity::class.java))
            }

            override fun allowTemporarily() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val database = logic.database

                    // this accesses the database directly because it is not synced
                    Threads.database.submit {
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
                    }
                }
            }

            override fun disableTemporarilyLockForAllCategories() {
                auth.tryDispatchParentActions(
                        userRelatedData.categories
                                .filter { it.category.temporarilyBlocked }
                                .map {
                                    UpdateCategoryTemporarilyBlockedAction(
                                            categoryId = it.category.id,
                                            blocked = false,
                                            endTime = null
                                    )
                                }
                )
            }

            override fun disableTemporarilyLockForCurrentCategory() {
                blockedCategoryId ?: return

                auth.tryDispatchParentAction(
                        UpdateCategoryTemporarilyBlockedAction(
                                categoryId = blockedCategoryId,
                                blocked = false,
                                endTime = null
                        )
                )
            }

            override fun showAuthenticationScreen() {
                (activity as LockActivity).showAuthenticationScreen()
            }
        }
    }

    private fun bindAddToCategoryOptions(userRelatedData: UserRelatedData) {
        binding.addToCategoryOptions.removeAllViews()

        userRelatedData.categories.sortedCategories().forEach { category ->
            LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
                button.title = category.category.title
                button.button.setOnClickListener { _ ->
                    auth.tryDispatchParentAction(
                            AddCategoryAppsAction(
                                    categoryId = category.category.id,
                                    packageNames = listOf(packageName)
                            )
                    )
                }
            }
        }

        LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
            button.title = getString(R.string.create_category_title)
            button.button.setOnClickListener {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    CreateCategoryDialogFragment
                            .newInstance(ManageChildFragmentArgs(childId = userRelatedData.user.id))
                            .show(fragmentManager!!)
                }
            }
        }
    }

    private fun bindExtraTimeView(categoryId: String, timeZone: TimeZone) {
        binding.extraTimeBtnOk.setOnClickListener {
            binding.extraTimeSelection.clearNumberPickerFocus()

            if (auth.requestAuthenticationOrReturnTrue()) {
                val extraTimeToAdd = binding.extraTimeSelection.timeInMillis

                if (extraTimeToAdd > 0) {
                    binding.extraTimeBtnOk.isEnabled = false

                    val date = DateInTimezone.newInstance(auth.logic.timeApi.getCurrentTimeInMillis(), timeZone)

                    auth.tryDispatchParentAction(IncrementCategoryExtraTimeAction(
                            categoryId = categoryId,
                            addedExtraTime = extraTimeToAdd,
                            extraTimeDay = if (binding.switchLimitExtraTimeToToday.isChecked) date.dayOfEpoch else -1
                    ))

                    binding.extraTimeBtnOk.isEnabled = true
                }
            }
        }
    }

    private fun initExtraTimeView() {
        binding.extraTimeTitle.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.lock_extratime_title,
                    text = R.string.lock_extratime_text
            ).show(parentFragmentManager)
        }

        logic.database.config().getEnableAlternativeDurationSelectionAsync().observe(viewLifecycleOwner, Observer {
            binding.extraTimeSelection.enablePickerMode(it)
        })

        binding.extraTimeSelection.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                binding.extraTimeBtnOk.visibility = if (newTimeInMillis == 0L) View.GONE else View.VISIBLE
            }

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    logic.database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LockFragmentBinding.inflate(layoutInflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        deviceAndUserRelatedData.observe(viewLifecycleOwner, Observer { update() })
        batteryStatus.observe(viewLifecycleOwner, Observer { update() })

        binding.packageName = packageName

        binding.appTitle = title ?: "???"
        binding.appIcon.setImageDrawable(logic.platformIntegration.getAppIcon(packageName))

        initExtraTimeView()

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        logic.realTimeLogic.registerTimeModificationListener(timeModificationListener)

        update()
    }

    override fun onPause() {
        super.onPause()

        logic.realTimeLogic.unregisterTimeModificationListener(timeModificationListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        unscheduleUpdate()
    }
}

interface Handlers {
    fun openMainApp()
    fun allowTemporarily()
    fun disableTemporarilyLockForCurrentCategory()
    fun disableTemporarilyLockForAllCategories()
    fun showAuthenticationScreen()
}
