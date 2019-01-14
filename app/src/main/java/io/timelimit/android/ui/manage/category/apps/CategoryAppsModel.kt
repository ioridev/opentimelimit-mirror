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
package io.timelimit.android.ui.manage.category.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import java.util.*

class CategoryAppsModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database
    private val args = MutableLiveData<ManageCategoryFragmentArgs>()

    fun init(args: ManageCategoryFragmentArgs) {
        if (this.args.value != args) {
            this.args.value = args
        }
    }

    private val installedApps = database.app().getApps()

    private val appsOfThisCategory = args.switchMap { database.categoryApp().getCategoryApps(it.categoryId) }

    private val appsOfCategoryWithNames = installedApps.switchMap { allApps ->
        appsOfThisCategory.map { apps ->
            apps.map { categoryApp ->
                categoryApp to allApps.find { app -> app.packageName == categoryApp.packageName }
            }
        }
    }

    val appEntries = appsOfCategoryWithNames.map { apps ->
        apps.map { (app, appEntry) ->
            if (appEntry != null) {
                AppEntry(appEntry.title, app.packageName)
            } else {
                AppEntry("app not found", app.packageName)
            }
        }.sortedBy { it.title.toLowerCase(Locale.US) }
    }
}
