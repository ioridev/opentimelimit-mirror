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
package io.timelimit.android.ui.manage.child.advanced.manageblocktemporarily

import androidx.lifecycle.LiveData
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.integration.time.TimeApi
import io.timelimit.android.livedata.*

data class ManageBlockTemporarilyItem(
        val categoryId: String,
        val categoryTitle: String,
        val checked: Boolean,
        val endTime: Long
)

object ManageBlockTemporarilyItems {
    fun build(
            userRelatedData: LiveData<UserRelatedData?>,
            timeApi: TimeApi
    ): LiveData<List<ManageBlockTemporarilyItem>> {
        val time = liveDataFromFunction { timeApi.getCurrentTimeInMillis() }

        return userRelatedData.map { userRelatedData ->
            userRelatedData?.sortedCategories()?.map { (_, category) ->
                ManageBlockTemporarilyItem(
                        categoryId = category.category.id,
                        categoryTitle = category.category.title,
                        endTime = category.category.temporarilyBlockedEndTime,
                        checked = category.category.temporarilyBlocked
                )
            } ?: emptyList()
        }.switchMap { items ->
            val hasEndtimes = items.find { it.endTime != 0L } != null

            if (hasEndtimes) {
                time.map { time ->
                    items.map { item ->
                        if (item.endTime == 0L || item.endTime >= time) {
                            item
                        } else {
                            item.copy(checked = false)
                        }
                    }
                }
            } else {
                liveDataFromValue(items)
            }
        }.ignoreUnchanged()
    }
}