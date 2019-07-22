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
package io.timelimit.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.overview.main.MainFragment
import io.timelimit.android.ui.setup.SetupTermsFragment

class MainActivity : AppCompatActivity(), ActivityViewModelHolder {
    companion object {
        private const val AUTH_DIALOG_TAG = "adt"
    }

    private val currentNavigatorFragment = MutableLiveData<Fragment>()

    override var ignoreStop: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // prepare livedata
        val customTitle = currentNavigatorFragment.switchMap {
            if (it != null && it is FragmentWithCustomTitle) {
                it.getCustomTitle()
            } else {
                liveDataFromValue(null as String?)
            }
        }.ignoreUnchanged()

        val title = Transformations.map(customTitle) {
            if (it == null) {
                getString(R.string.app_name)
            } else {
                it
            }
        }

        // up button
        val shouldShowBackButtonForNavigatorFragment = currentNavigatorFragment.map { fragment ->
            (!(fragment is MainFragment)) && (!(fragment is SetupTermsFragment))
        }

        val shouldShowUpButton = shouldShowBackButtonForNavigatorFragment

        shouldShowUpButton.observe(this, Observer { supportActionBar!!.setDisplayHomeAsUpEnabled(it) })

        // init if not yet done
        DefaultAppLogic.with(this)

        val fragmentContainer = supportFragmentManager.findFragmentById(R.id.nav_host)!!
        val fragmentContainerManager = fragmentContainer.childFragmentManager
        getNavController().addOnDestinationChangedListener { _, _, _ ->
            Threads.mainThreadHandler.post {
                currentNavigatorFragment.value = fragmentContainerManager.fragments.first()
            }
        }

        title.observe(this, Observer { setTitle(it) })
    }

    override fun onOptionsItemSelected(item: MenuItem) = when {
        item.itemId == android.R.id.home -> {
            onBackPressed()

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()

        if ((!isChangingConfigurations) && (!ignoreStop)) {
            getActivityViewModel().logOut()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT == Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) {
            return
        }

        getNavController().popBackStack(R.id.overviewFragment, true)
        getNavController().handleDeepLink(
                getNavController().createDeepLink()
                        .setDestination(R.id.overviewFragment)
                        .createTaskStackBuilder()
                        .intents
                        .first()
        )
    }

    override fun getActivityViewModel(): ActivityViewModel {
        return ViewModelProviders.of(this).get(ActivityViewModel::class.java)
    }

    private fun getNavHostFragment(): NavHostFragment {
        return supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
    }

    private fun getNavController(): NavController {
        return getNavHostFragment().navController
    }

    override fun onBackPressed() {
        if (currentNavigatorFragment.value is SetupTermsFragment) {
            // hack to prevent the user from going to the launch screen of the App if it is not set up
            finish()
        } else {
            super.onBackPressed()
        }
    }

    override fun showAuthenticationScreen() {
        if (supportFragmentManager.findFragmentByTag(AUTH_DIALOG_TAG) == null) {
            NewLoginFragment().showSafe(supportFragmentManager, AUTH_DIALOG_TAG)
        }
    }
}
