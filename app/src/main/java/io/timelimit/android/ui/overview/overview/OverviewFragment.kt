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

package io.timelimit.android.ui.overview.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.CoroutineFragment
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import kotlinx.android.synthetic.main.fragment_overview.*

class OverviewFragment : CoroutineFragment() {
    private val handlers: OverviewFragmentParentHandlers by lazy { parentFragment as OverviewFragmentParentHandlers }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val model: OverviewFragmentModel by lazy {
        ViewModelProviders.of(this).get(OverviewFragmentModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OverviewFragmentAdapter()

        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(context!!)

        adapter.handlers = object: OverviewFragmentHandlers {
            override fun onAddUserClicked() {
                handlers.openAddUserScreen()
            }

            override fun onDeviceClicked(device: Device) {
                handlers.openManageDeviceScreen(deviceId = device.id)
            }

            override fun onUserClicked(user: User) {
                when (user.type) {
                    UserType.Child -> handlers.openManageChildScreen(childId = user.id)
                    UserType.Parent -> handlers.openManageParentScreen(parentId = user.id)
                }.let {  }
            }
        }

        model.listEntries.observe(this, Observer { adapter.data = it })

        ItemTouchHelper(
                object: ItemTouchHelper.Callback() {
                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        val index = viewHolder.adapterPosition
                        val item = if (index == RecyclerView.NO_POSITION) null else adapter.data!![index]

                        if (item == OverviewFragmentHeaderIntro) {
                            return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END or ItemTouchHelper.START) or
                                    makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.START)
                        } else {
                            return 0
                        }
                    }

                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        // remove the introduction header
                        Threads.database.execute {
                            logic.database.config().setHintsShownSync(HintsToShow.OVERVIEW_INTRODUCTION)
                        }
                    }
                }
        ).attachToRecyclerView(recycler)
    }
}

interface OverviewFragmentParentHandlers {
    fun openAddUserScreen()
    fun openManageDeviceScreen(deviceId: String)
    fun openManageChildScreen(childId: String)
    fun openManageParentScreen(parentId: String)
}
