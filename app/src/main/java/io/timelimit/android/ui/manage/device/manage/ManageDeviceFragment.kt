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
package io.timelimit.android.ui.manage.device.manage

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.FragmentManageDeviceBinding
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.android.AdminReceiver
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.sync.actions.UpdateDeviceNameAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ManageDeviceFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        private const val IS_EDITING_DEVICE_TITLE = "a"
    }

    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFragmentArgs by lazy { ManageDeviceFragmentArgs.fromBundle(arguments!!) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    private var isEditingDeviceTitle = MutableLiveData<Boolean>().apply { value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            isEditingDeviceTitle.value = savedInstanceState.getBoolean(IS_EDITING_DEVICE_TITLE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(IS_EDITING_DEVICE_TITLE, isEditingDeviceTitle.value!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = FragmentManageDeviceBinding.inflate(inflater, container, false)
        val userEntries = logic.database.user().getAllUsersLive()

        ManageDeviceManipulation.bindView(
                binding = binding.manageManipulation,
                deviceEntry = deviceEntry,
                lifecycleOwner = this,
                activityViewModel = auth
        )

        val userSpinnerAdapter = ArrayAdapter<String>(context!!, android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        // label, id
        val userListItems = ArrayList<Pair<String, String>>()

        fun bindUserListItems() {
            userSpinnerAdapter.clear()
            userSpinnerAdapter.addAll(userListItems.map { it.first })
            userSpinnerAdapter.notifyDataSetChanged()
        }

        fun bindUserListSelection() {
            val selectedUserId = deviceEntry.value?.currentUserId

            val selectedIndex = userListItems.indexOfFirst { it.second == selectedUserId }

            if (selectedIndex != -1) {
                binding.userSpinner.setSelection(selectedIndex)
            } else {
                val fallbackSelectedIndex = userListItems.indexOfFirst { it.second == "" }

                if (fallbackSelectedIndex != -1) {
                    binding.userSpinner.setSelection(fallbackSelectedIndex)
                }
            }
        }

        binding.handlers = object: ManageDeviceFragmentHandlers {
            override fun openUsageStatsSettings() {
                if (binding.isThisDevice == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }

            override fun openNotificationAccessSettings() {
                if (binding.isThisDevice == true) {
                    try {
                        startActivity(
                                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (ex: Exception) {
                        Toast.makeText(
                                context,
                                R.string.error_general,
                                Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun manageDeviceAdmin() {
                if (binding.isThisDevice == true) {
                    val protectionLevel = logic.platformIntegration.getCurrentProtectionLevel()

                    if (protectionLevel == ProtectionLevel.None) {
                        if (InformAboutDeviceOwnerDialogFragment.shouldShow) {
                            startActivity(
                                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                            .putExtra(
                                                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                    ComponentName(context!!, AdminReceiver::class.java)
                                            )
                            )
                        } else {
                            InformAboutDeviceOwnerDialogFragment().show(fragmentManager!!)
                        }
                    } else {
                        startActivity(
                                Intent(Settings.ACTION_SECURITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }

            override fun startEditDeviceTitle() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    binding.newDeviceTitleText.setText(binding.deviceTitle)
                    isEditingDeviceTitle.value = true
                }
            }

            override fun doEditDeviceTitle() {
                val newDeviceTitle = binding.newDeviceTitleText.text.toString()

                if (newDeviceTitle.isBlank()) {
                    Snackbar.make(binding.newDeviceTitleText, R.string.manage_device_rename_toast_empty, Snackbar.LENGTH_SHORT).show()
                } else {
                    if (auth.tryDispatchParentAction(
                                    UpdateDeviceNameAction(
                                            deviceId = args.deviceId,
                                            name = newDeviceTitle
                                    )
                            )) {
                        isEditingDeviceTitle.value = false
                    }
                }
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        binding.userSpinner.adapter = userSpinnerAdapter
        binding.userSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = userListItems[position]
                val userId = item.second
                val device = deviceEntry.value

                if (device != null) {
                    if (device.currentUserId != userId) {
                        if (!auth.tryDispatchParentAction(
                                        SetDeviceUserAction(
                                                deviceId = args.deviceId,
                                                userId = userId
                                        )
                                )) {
                            bindUserListSelection()
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing to do
            }
        }

        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack()
            } else {
                val now = logic.timeApi.getCurrentTimeInMillis()

                binding.deviceTitle = device.name
                binding.modelString = device.model
                binding.addedAtString = getString(R.string.manage_device_added_at, DateUtils.getRelativeTimeSpanString(
                        device.addedAt,
                        now,
                        DateUtils.HOUR_IN_MILLIS

                ))
                binding.usageStatsAccess = device.currentUsageStatsPermission
                binding.notificationAccessPermission = device.currentNotificationAccessPermission
                binding.protectionLevel = device.currentProtectionLevel
                binding.didAppDowngrade = device.currentAppVersion < device.highestAppVersion
            }
        })

        mergeLiveData(deviceEntry, userEntries).observe(this, Observer {
            val (device, users) = it!!

            if (device != null && users != null) {
                userListItems.clear()
                userListItems.addAll(
                        users.map { user -> Pair(user.name, user.id) }
                )
                userListItems.add(Pair(getString(R.string.manage_device_current_user_none), ""))

                bindUserListItems()
                bindUserListSelection()
            }
        })

        logic.deviceId.observe(this, Observer {
            ownDeviceId ->

            binding.isThisDevice = ownDeviceId == args.deviceId
        })

        isEditingDeviceTitle.observe(this, Observer { binding.isEditingDeviceTitle = it })

        ManageDeviceIntroduction.bind(
                view = binding.introduction,
                database = logic.database,
                lifecycleOwner = this
        )

        val userEntry = deviceEntry.switchMap {
            device ->

            userEntries.map { users ->
                users.find { user -> user.id == device?.currentUserId }
            }
        }

        UsageStatsAccessRequiredAndMissing.bind(
                view = binding.usageStatsAccessMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        logic.backgroundTaskLogic.syncDeviceStatusAsync()
    }

    override fun getCustomTitle() = deviceEntry.map { it?.name }
}

interface ManageDeviceFragmentHandlers {
    fun openUsageStatsSettings()
    fun openNotificationAccessSettings()
    fun manageDeviceAdmin()
    fun startEditDeviceTitle()
    fun doEditDeviceTitle()
    fun showAuthenticationScreen()
}
