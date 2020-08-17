package io.timelimit.android.ui.manage.category.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryTitleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.util.EditTextBottomSheetDialog

class RenameCategoryDialogFragment: EditTextBottomSheetDialog() {
    companion object {
        private const val TAG = "RenameCategoryDialogFragment"

        fun newInstance(args: ManageCategoryFragmentArgs) = RenameCategoryDialogFragment().apply {
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

        binding.title = getString(R.string.category_settings_rename)

        if (savedInstanceState == null) {
            runAsync {
                categoryEntry.waitForNullableValue()?.let { entry ->
                    binding.editText.setText(entry.title)
                    didInitField()
                }
            }
        }
    }

    override fun go() {
        val newTitle = binding.editText.text.toString()

        if (newTitle.isBlank()) {
            Toast.makeText(context!!, R.string.category_settings_rename_empty, Toast.LENGTH_SHORT).show()
        } else {
            auth.tryDispatchParentAction(
                    UpdateCategoryTitleAction(
                            categoryId = params.categoryId,
                            newTitle = newTitle
                    )
            )
        }

        dismiss()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
