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
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
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
import io.timelimit.android.ui.view.KeyboardViewListener

class NewLoginFragment: DialogFragment() {
    companion object {
        private const val SELECTED_USER_ID = "selectedUserId"
    }

    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val model: LoginPasswordDialogFragmentModel by lazy {
        ViewModelProviders.of(this).get(LoginPasswordDialogFragmentModel::class.java)
    }
    private val inputMethodManager: InputMethodManager by lazy {
        context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState?.containsKey(SELECTED_USER_ID) == true) {
            model.selectedUserId.value = savedInstanceState.getString(SELECTED_USER_ID)
        }

        if (savedInstanceState == null) {
            model.tryDefaultLogin(getActivityViewModel(activity!!))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = object: BottomSheetDialog(context!!, theme) {
        override fun onBackPressed() {
            if (model.selectedUserId.value == null) {
                super.onBackPressed()
            } else {
                model.selectedUserId.value = null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        model.selectedUserId.value?.let { outState.putString(SELECTED_USER_ID, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = NewLoginFragmentBinding.inflate(inflater, container, false)

        binding.userList.apply {
            val adapter = LoginUserAdapter()

            logic.database.user().getParentUsersLive().observe(this@NewLoginFragment, Observer { adapter.data = it })

            adapter.listener = object: LoginUserAdapterListener {
                override fun onUserClicked(user: User) {
                    model.selectedUserId.value = user.id
                }
            }

            recycler.adapter = adapter
            recycler.layoutManager = LinearLayoutManager(context)
        }

        binding.enterPassword.apply {
            showKeyboardButton.setOnClickListener {
                showCustomKeyboard = !showCustomKeyboard

                if (showCustomKeyboard) {
                    inputMethodManager.hideSoftInputFromWindow(password.windowToken, 0)
                } else {
                    inputMethodManager.showSoftInput(password, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    password.showSoftInputOnFocus = !showCustomKeyboard
                }
            }

            fun tryLogin() {
                model.tryLogin(
                        password = password.text.toString(),
                        model = getActivityViewModel(activity!!)
                )
            }

            keyboard.listener = object: KeyboardViewListener {
                override fun onItemClicked(content: String) {
                    val start = Math.max(password.selectionStart, 0)
                    val end = Math.max(password.selectionEnd, 0)

                    password.text.replace(Math.min(start, end), Math.max(start, end), content, 0, content.length)
                }

                override fun onGoClicked() {
                    tryLogin()
                }
            }

            password.setOnEnterListenr {
                tryLogin()
            }

            model.status.observe(this@NewLoginFragment, Observer { status ->
                when (status!!) {
                    is UserListLoginDialogStatus -> {
                        if (binding.switcher.displayedChild != 0) {
                            binding.switcher.setInAnimation(context!!, R.anim.wizard_close_step_in)
                            binding.switcher.setOutAnimation(context!!, R.anim.wizard_close_step_out)
                            binding.switcher.displayedChild = 0
                        }

                        null
                    }
                    is WrongTimeLoginDialogStatus -> {
                        if (binding.switcher.displayedChild != 2) {
                            binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                            binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                            binding.switcher.displayedChild = 2
                        }

                        null
                    }
                    WaitingForPasswordLoginDialogStatus -> {
                        if (binding.switcher.displayedChild != 1) {
                            binding.switcher.setInAnimation(context!!, R.anim.wizard_open_step_in)
                            binding.switcher.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                            binding.switcher.displayedChild = 1
                        }

                        password.isEnabled = true

                        binding.enterPassword.password.requestFocus()
                        inputMethodManager.showSoftInput(binding.enterPassword.password, 0)

                        null
                    }
                    ValidationRunningLoginDialogStatus -> {
                        password.isEnabled = false
                    }
                    WrongPasswordLoginDialogStatus -> {
                        Toast.makeText(context!!, R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        password.setText("")

                        model.resetPasswordWrong()
                    }
                    SuccessLoginDialogStatus -> dismissAllowingStateLoss()
                }.let {/* require handling all paths */}
            })
        }

        return binding.root
    }
}
