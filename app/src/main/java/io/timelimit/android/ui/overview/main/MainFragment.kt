/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.overview.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.contacts.ContactsFragment
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.obsolete.ObsoleteDialogFragment
import io.timelimit.android.ui.overview.about.AboutFragment
import io.timelimit.android.ui.overview.about.AboutFragmentParentHandlers
import io.timelimit.android.ui.overview.overview.OverviewFragment
import io.timelimit.android.ui.overview.overview.OverviewFragmentParentHandlers
import io.timelimit.android.ui.overview.uninstall.UninstallFragment
import kotlinx.android.synthetic.main.fragment_main.*

class MainFragment : Fragment(), OverviewFragmentParentHandlers, AboutFragmentParentHandlers, FragmentWithCustomTitle {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private lateinit var navigation: NavController
    private val showAuthButtonLive = MutableLiveData<Boolean>()
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private var didRedirectToUserScreen = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        navigation = Navigation.findNavController(container!!)

        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AuthenticationFab.manageAuthenticationFab(
                fab = fab,
                fragment = this,
                shouldHighlight = activity.getActivityViewModel().shouldHighlightAuthenticationButton,
                authenticatedUser = activity.getActivityViewModel().authenticatedUser,
                doesSupportAuth = showAuthButtonLive
        )

        fab.setOnClickListener { activity.showAuthenticationScreen() }

        logic.isInitialized.switchMap { isInitialized ->
            if (isInitialized) {
                logic.database.config().getOwnDeviceId().map { it == null }
            } else {
                liveDataFromValue(false)
            }
        }.observe(viewLifecycleOwner, Observer { shouldShowSetup ->
            if (shouldShowSetup == true) {
                runAsync {
                    val hasParentKey = Threads.database.executeAndWait { logic.database.config().getParentModeKeySync() != null }

                    if (isAdded && parentFragmentManager.isStateSaved == false) {
                        if (hasParentKey) {
                            navigation.safeNavigate(
                                    MainFragmentDirections.actionOverviewFragmentToParentModeFragment(),
                                    R.id.overviewFragment
                            )
                        } else {
                            navigation.safeNavigate(
                                    MainFragmentDirections.actionOverviewFragmentToSetupTermsFragment(),
                                    R.id.overviewFragment
                            )
                        }
                    }
                }
            } else {
                if (savedInstanceState == null && !didRedirectToUserScreen) {
                    didRedirectToUserScreen = true

                    runAsync {
                        val user = logic.deviceUserEntry.waitForNullableValue()

                        if (user?.type == UserType.Child) {
                            if (isAdded && parentFragmentManager.isStateSaved == false) {
                                openManageChildScreen(user.id)
                            }
                        }

                        if (user != null) {
                            if (isAdded && parentFragmentManager.isStateSaved == false) {
                                ObsoleteDialogFragment.show(getActivity()!!, false)
                            }
                        }
                    }
                }
            }
        })

        fun updateShowFab(selectedItemId: Int) {
            showAuthButtonLive.value = when (selectedItemId) {
                R.id.main_tab_overview -> true
                R.id.main_tab_contacts -> true
                R.id.main_tab_uninstall -> true
                R.id.main_tab_about -> false
                else -> throw IllegalStateException()
            }
        }

        bottom_navigation_view.setOnNavigationItemReselectedListener { /* ignore */ }
        bottom_navigation_view.setOnNavigationItemSelectedListener { menuItem ->
            if (childFragmentManager.isStateSaved) {
                false
            } else {
                childFragmentManager.beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .replace(R.id.container, when (menuItem.itemId) {
                            R.id.main_tab_overview -> OverviewFragment()
                            R.id.main_tab_contacts -> ContactsFragment()
                            R.id.main_tab_uninstall -> UninstallFragment()
                            R.id.main_tab_about -> AboutFragment()
                            else -> throw IllegalStateException()
                        })
                        .commit()

                updateShowFab(menuItem.itemId)

                true
            }
        }

        if (childFragmentManager.findFragmentById(R.id.container) == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.container, OverviewFragment())
                    .commit()
        }

        updateShowFab(bottom_navigation_view.selectedItemId)
    }

    override fun openAddUserScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToAddUserFragment(),
                R.id.overviewFragment
        )
    }

    override fun openManageChildScreen(childId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageChildFragment(childId),
                R.id.overviewFragment
        )
    }

    override fun openManageDeviceScreen(deviceId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageDeviceFragment(deviceId),
                R.id.overviewFragment
        )
    }

    override fun openManageParentScreen(parentId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageParentFragment(parentId),
                R.id.overviewFragment
        )
    }

    override fun onShowDiagnoseScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToDiagnoseMainFragment(),
                R.id.overviewFragment
        )
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromValue("${getString(R.string.main_tab_overview)} (${getString(R.string.app_name)})")
}
