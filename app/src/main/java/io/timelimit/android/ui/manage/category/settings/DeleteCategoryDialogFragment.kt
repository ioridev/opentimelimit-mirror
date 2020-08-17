package io.timelimit.android.ui.manage.category.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.DeleteCategoryAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.util.ConfirmDeleteDialogFragment

class DeleteCategoryDialogFragment: ConfirmDeleteDialogFragment() {
    companion object {
        private const val TAG = "DeleteCategoryDialogFragment"

        fun newInstance(args: ManageCategoryFragmentArgs) = DeleteCategoryDialogFragment().apply {
            arguments = args.toBundle()
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    val categoryEntry: LiveData<Category?> by lazy {
        appLogic.database.category().getCategoryByChildIdAndId(
                categoryId = params.categoryId,
                childId = params.childId
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        categoryEntry.observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryEntry.observe(this, Observer {
            binding.text = getString(R.string.category_settings_delete_dialog, it?.title)
        })
    }

    override fun onConfirmDeletion() {
        auth.tryDispatchParentAction(DeleteCategoryAction(
                categoryId = params.categoryId
        ))

        dismiss()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
