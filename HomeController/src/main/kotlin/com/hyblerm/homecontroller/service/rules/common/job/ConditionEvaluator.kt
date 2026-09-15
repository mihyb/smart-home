package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.Condition
import com.hyblerm.homecontroller.config.ConfigurationProperties.Operator

/**
 * Compares an item state with a configured value.
 *
 * Returns null for "no answer" rather than false. An item reads NULL before its
 * first update and UNDEF while its thing is offline, and an ordering operator
 * has nothing to say about a string. A caller that must decide something treats
 * null as "not true"; an alarm holds its state machine still instead, because
 * "the fence voltage is unknown" is not "the fence voltage is fine".
 */
object ConditionEvaluator {

    private val LEADING_NUMBER = Regex("^[-+]?\\d+(\\.\\d+)?")

    fun matches(state: String, condition: Condition): Boolean? {
        val undefined = state == "NULL" || state == "UNDEF"
        if (undefined && condition.op != Operator.UNDEF && condition.op != Operator.DEFINED) {
            return null
        }
        return when (condition.op) {
            Operator.UNDEF -> undefined
            Operator.DEFINED -> !undefined
            Operator.EQ -> state.equals(condition.value, ignoreCase = true)
            Operator.NE -> !state.equals(condition.value, ignoreCase = true)
            else -> compareNumbers(state, condition)
        }
    }

    private fun compareNumbers(state: String, condition: Condition): Boolean? {
        // A Number:ElectricPotential item reads "5900 V" over REST, not "5900":
        // openHAB renders the unit into the state and toDouble chokes on it.
        val left = LEADING_NUMBER.find(state.trim())?.value?.toDoubleOrNull() ?: return null
        val right = condition.value.trim().toDoubleOrNull() ?: return null
        return when (condition.op) {
            Operator.LT -> left < right
            Operator.LTE -> left <= right
            Operator.GT -> left > right
            Operator.GTE -> left >= right
            else -> null
        }
    }
}
