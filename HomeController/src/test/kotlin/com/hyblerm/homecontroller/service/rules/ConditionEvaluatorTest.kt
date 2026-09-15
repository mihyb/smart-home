package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.Condition
import com.hyblerm.homecontroller.config.ConfigurationProperties.Operator
import com.hyblerm.homecontroller.service.rules.common.job.ConditionEvaluator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val ITEM = "fence_upper_voltage"

class ConditionEvaluatorTest {

    @Test
    fun `equality ignores case`() {
        assertThat(ConditionEvaluator.matches("RUNNING", condition("running", Operator.EQ))).isTrue()
        assertThat(ConditionEvaluator.matches("RUNNING", condition("STOPPED", Operator.EQ))).isFalse()
    }

    @Test
    fun `inequality is the opposite`() {
        assertThat(ConditionEvaluator.matches("RUNNING", condition("RUNNING", Operator.NE))).isFalse()
        assertThat(ConditionEvaluator.matches("RUNNING", condition("STOPPED", Operator.NE))).isTrue()
    }

    @Test
    fun `numbers are compared without their unit`() {
        // A Number:ElectricPotential item reads "5900 V" over REST, not "5900".
        // toDouble throws on that, which would have made every fence alarm
        // silently unevaluable.
        assertThat(ConditionEvaluator.matches("5900 V", condition("5000", Operator.LT))).isFalse()
        assertThat(ConditionEvaluator.matches("4900 V", condition("5000", Operator.LT))).isTrue()
        assertThat(ConditionEvaluator.matches("11500 V", condition("11000", Operator.GT))).isTrue()
        assertThat(ConditionEvaluator.matches("17.19 °C", condition("5", Operator.GT))).isTrue()
        assertThat(ConditionEvaluator.matches("-3.5 °C", condition("0", Operator.LT))).isTrue()
    }

    @Test
    fun `boundaries belong to the inclusive operators`() {
        assertThat(ConditionEvaluator.matches("5000 V", condition("5000", Operator.LT))).isFalse()
        assertThat(ConditionEvaluator.matches("5000 V", condition("5000", Operator.LTE))).isTrue()
        assertThat(ConditionEvaluator.matches("5000 V", condition("5000", Operator.GT))).isFalse()
        assertThat(ConditionEvaluator.matches("5000 V", condition("5000", Operator.GTE))).isTrue()
    }

    @Test
    fun `an item without a state has no answer`() {
        // Not false: the thing is offline, which says nothing about the fence.
        assertThat(ConditionEvaluator.matches("NULL", condition("5000", Operator.LT))).isNull()
        assertThat(ConditionEvaluator.matches("UNDEF", condition("5000", Operator.LT))).isNull()
        assertThat(ConditionEvaluator.matches("UNDEF", condition("RUNNING", Operator.EQ))).isNull()
    }

    @Test
    fun `a state that is not a number has no answer for an ordering operator`() {
        assertThat(ConditionEvaluator.matches("RUNNING", condition("5000", Operator.LT))).isNull()
        assertThat(ConditionEvaluator.matches("5900 V", condition("nonsense", Operator.LT))).isNull()
    }

    @Test
    fun `UNDEF asks whether the item has a state at all`() {
        // The one question the other operators cannot answer: they all report
        // "unknown" here, which is what an alarm has to be able to notice.
        assertThat(ConditionEvaluator.matches("UNDEF", condition("", Operator.UNDEF))).isTrue()
        assertThat(ConditionEvaluator.matches("NULL", condition("", Operator.UNDEF))).isTrue()
        assertThat(ConditionEvaluator.matches("5900 V", condition("", Operator.UNDEF))).isFalse()

        assertThat(ConditionEvaluator.matches("5900 V", condition("", Operator.DEFINED))).isTrue()
        assertThat(ConditionEvaluator.matches("UNDEF", condition("", Operator.DEFINED))).isFalse()
    }

    private fun condition(value: String, op: Operator) = Condition().apply {
        this.item = ITEM
        this.value = value
        this.op = op
    }
}
