package io.timelimit.android.ui.manage.category.timelimit_rules

import io.timelimit.android.data.model.TimeLimitRule

sealed class TimeLimitRuleItem
object AddTimeLimitRuleItem: TimeLimitRuleItem()
data class TimeLimitRuleRuleItem(val rule: TimeLimitRule): TimeLimitRuleItem()