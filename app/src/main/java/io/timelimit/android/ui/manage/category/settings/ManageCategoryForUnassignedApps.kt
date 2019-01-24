package io.timelimit.android.ui.manage.category.settings

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.data.Database
import io.timelimit.android.databinding.ManageCategoryForUnassignedAppsBinding
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map
import io.timelimit.android.sync.actions.SetCategoryForUnusedApps
import io.timelimit.android.ui.main.ActivityViewModel

object ManageCategoryForUnassignedApps {
    fun bind(
            binding: ManageCategoryForUnassignedAppsBinding,
            categoryId: String,
            childId: String,
            auth: ActivityViewModel,
            database: Database,
            lifecycleOwner: LifecycleOwner
    ) {
        val userEntry = database.user().getUserByIdLive(childId)
        val isCurrentlyChosen = userEntry.map { it?.categoryForNotAssignedApps == categoryId }.ignoreUnchanged()

        isCurrentlyChosen.observe(lifecycleOwner, Observer { binding.isThisCategoryUsed = it })

        binding.changeModeButton.setOnClickListener {
            val chosen = isCurrentlyChosen.value

            if (chosen == true) {
                auth.tryDispatchParentAction(
                        SetCategoryForUnusedApps(
                                childId = childId,
                                categoryId = ""
                        )
                )
            } else if (chosen == false) {
                auth.tryDispatchParentAction(
                        SetCategoryForUnusedApps(
                                childId = childId,
                                categoryId = categoryId
                        )
                )
            }
        }
    }
}