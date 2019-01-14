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
package io.timelimit.android.ui.manage.category.timelimit_rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.FragmentCategoryTimeLimitRuleItemBinding
import io.timelimit.android.util.JoinUtil
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class Adapter: RecyclerView.Adapter<ViewHolder>() {
    companion object {
        private const val TYPE_ITEM = 1
        private const val TYPE_ADD = 2
    }

    var data: List<TimeLimitRule>? by Delegates.observable(null as List<TimeLimitRule>?) { _, _, _ -> notifyDataSetChanged() }
    var usedTimes: List<Long>? by Delegates.observable(null as List<Long>?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? = null

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): TimeLimitRule {
        return data!![position]
    }

    override fun getItemId(position: Int): Long = when {
        position == data!!.size -> 1
        else -> getItem(position).id.hashCode().toLong()
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size + 1
        }
    }

    override fun getItemViewType(position: Int) = when {
        position == data!!.size -> TYPE_ADD
        else -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_ITEM -> {
            ItemViewHolder(
                    FragmentCategoryTimeLimitRuleItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )
        }
        TYPE_ADD -> {
            ViewHolder(
                    AddItemViewBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).apply {
                        label = parent.context.getString(R.string.category_time_limit_rule_dialog_new)
                        root.setOnClickListener { handlers?.onAddTimeLimitRuleClicked() }
                    }.root
            )
        }
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == data!!.size) {
            // nothing to do
        } else {
            val item = getItem(position)
            val binding = (holder as ItemViewHolder).view
            val dayNames = binding.root.resources.getStringArray(R.array.days_of_week_array)
            val usedTime = usedTimes?.mapIndexed { index, value ->
                if (item.dayMask.toInt() and (1 shl index) != 0) {
                    value
                } else {
                    0
                }
            }?.sum()?.toInt() ?: 0

            binding.maxTimeString = TimeTextUtil.time(item.maximumTimeInMillis, binding.root.context)
            binding.usageAsText = TimeTextUtil.used(usedTime, binding.root.context)
            binding.usageProgressInPercent = if (item.maximumTimeInMillis > 0)
                (usedTime * 100 / item.maximumTimeInMillis)
            else
                100
            binding.daysString = JoinUtil.join(
                    dayNames.filterIndexed { index, _ -> (item.dayMask.toInt() and (1 shl index)) != 0 },
                    binding.root.context
            )
            binding.appliesToExtraTime = item.applyToExtraTimeUsage
            binding.card.setOnClickListener { handlers?.onTimeLimitRuleClicked(item) }

            binding.executePendingBindings()
        }
    }
}

open class ViewHolder(view: View): RecyclerView.ViewHolder(view)
class ItemViewHolder(val view: FragmentCategoryTimeLimitRuleItemBinding): ViewHolder(view.root)

interface Handlers {
    fun onTimeLimitRuleClicked(rule: TimeLimitRule)
    fun onAddTimeLimitRuleClicked()
}
