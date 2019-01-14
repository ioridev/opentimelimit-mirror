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
package io.timelimit.android.ui.overview.about


import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.timelimit.android.databinding.FragmentAboutBinding
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic

class AboutFragment : Fragment() {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val listener: AboutFragmentParentHandlers by lazy { parentFragment as AboutFragmentParentHandlers }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAboutBinding.inflate(inflater, container, false)

        binding.sourceCodeUrl.movementMethod = LinkMovementMethod.getInstance()
        binding.termsText.movementMethod = LinkMovementMethod.getInstance()
        binding.containedSoftwareText.movementMethod = LinkMovementMethod.getInstance()

        ResetShownHints.bind(
                binding = binding.resetShownHintsView,
                lifecycleOwner = this,
                database = logic.database
        )

        binding.errorDiagnoseCard.setOnClickListener {
            listener.onShowDiagnoseScreen()
        }

        return binding.root
    }
}

interface AboutFragmentParentHandlers {
    fun onShowDiagnoseScreen()
}
