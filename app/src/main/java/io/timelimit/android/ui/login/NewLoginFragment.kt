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
package io.timelimit.android.ui.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.NewLoginFragmentBinding
import io.timelimit.android.extensions.setOnEnterListenr
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.getActivityViewModel

class NewLoginFragment: DialogFragment() {
    companion object {
        private const val SELECTED_USER_ID = "selectedUserId"
    }

    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val model: LoginPasswordDialogFragmentModel by lazy {
        ViewModelProviders.of(this).get(LoginPasswordDialogFragmentModel::class.java)
    }
    private val inputMethodManager: InputMethodManager by lazy {
        context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState?.containsKey(SELECTED_USER_ID) == true) {
            selectedUserId.value = savedInstanceState.getString(SELECTED_USER_ID)
        }

        if (savedInstanceState == null) {
            model.tryDefaultLogin(getActivityViewModel(activity!!))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = object: BottomSheetDialog(context!!, theme) {
        override fun onBackPressed() {
            if (selectedUserId.value == null) {
                super.onBackPressed()
            } else {
                selectedUserId.value = null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        selectedUserId.value?.let { outState.putString(SELECTED_USER_ID, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = NewLoginFragmentBinding.inflate(inflater, container, false)

        binding.userList.apply {
            val adapter = LoginUserAdapter()

            logic.database.user().getParentUsersLive().observe(this@NewLoginFragment, Observer { adapter.data = it })

            adapter.listener = object: LoginUserAdapterListener {
                override fun onUserClicked(user: User) {
                    selectedUserId.value = user.id
                }
            }

            recycler.adapter = adapter
            recycler.layoutManager = LinearLayoutManager(context)
        }

        binding.enterPassword.apply {
            fun tryLogin() {
                model.tryLogin(
                        userId = selectedUserId.value!!,
                        password = password.text.toString(),
                        model = getActivityViewModel(activity!!)
                )
            }

            password.setOnEnterListenr {
                tryLogin()
            }

            model.status.observe(this@NewLoginFragment, Observer {
                status ->

                val readyForInput = status == LoginPasswordDialogFragmentStatus.Idle

                password.isEnabled = readyForInput

                when (status) {
                    LoginPasswordDialogFragmentStatus.Working -> {/* nothing to do */}
                    LoginPasswordDialogFragmentStatus.Idle -> {/* nothing to do */}
                    LoginPasswordDialogFragmentStatus.PasswordWrong -> {
                        Toast.makeText(context!!, R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        password.setText("")

                        model.resetPasswordWrong()
                    }
                    LoginPasswordDialogFragmentStatus.Success -> {
                        dismissAllowingStateLoss()
                    }
                    LoginPasswordDialogFragmentStatus.PermanentlyFailed -> selectedUserId.value = null
                    null -> {/* nothing to handle */}
                }.let {  }
            })
        }

        selectedUserId.observe(this, Observer {
            if (it == null) {
                if (binding.switcher.displayedChild != 0) {
                    binding.switcher.setInAnimation(context!!, R.anim.wizard_close_step_in)
                    binding.switcher.setOutAnimation(context!!, R.anim.wizard_close_step_out)
                    binding.switcher.displayedChild = 0
                }
            } else {
                if (binding.switcher.displayedChild != 1) {
                    binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                    binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                    binding.switcher.displayedChild = 1
                }

                binding.enterPassword.password.requestFocus()
                inputMethodManager.showSoftInput(binding.enterPassword.password, 0)
            }
        })

        return binding.root
    }
}
