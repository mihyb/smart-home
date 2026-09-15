package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.AlarmJobConfig
import com.hyblerm.homecontroller.config.ConfigurationProperties.Alarms
import com.hyblerm.homecontroller.config.ConfigurationProperties.Condition
import com.hyblerm.homecontroller.config.ConfigurationProperties.Operator
import com.hyblerm.homecontroller.config.ConfigurationProperties.Severity
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.AlarmJob
import com.hyblerm.homecontroller.service.rules.common.job.AlarmState
import com.hyblerm.homecontroller.service.util.Time
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

private const val MESSAGE_ITEM = "notify_message"
private const val STATE_ITEM = "fence_upper_state"
private const val VOLTAGE_ITEM = "fence_upper_voltage"
private const val ALARM_ID = "fence-upper-low"

private val NOON = Instant.parse("2026-09-15T10:00:00Z")
private val ZONE = ZoneId.of("Europe/Prague")

class AlarmJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val time = mock(Time::class.java)

    @Test
    fun `nothing is sent while the conditions do not hold`() {
        live(voltage = "5900 V")

        val state = check(AlarmState(), NOON)

        verify(dataAccess, never()).commandItem(eq(MESSAGE_ITEM), any())
        assertThat(state.phase).isEqualTo(AlarmState.Phase.CLEAR)
    }

    @Test
    fun `the first message waits out the delay`() {
        live(voltage = "4200 V")

        val pending = check(AlarmState(), NOON)
        verify(dataAccess, never()).commandItem(eq(MESSAGE_ITEM), any())
        assertThat(pending.phase).isEqualTo(AlarmState.Phase.PENDING)

        val firing = check(pending, NOON.plusSeconds(120))

        verify(dataAccess).commandItem(MESSAGE_ITEM, "ALARM|$ALARM_ID|Horni ohradnik 4200 V")
        assertThat(firing.sent).isEqualTo(1)
    }

    @Test
    fun `a brief dip never reaches the first message`() {
        // The delay is the whole point: a beast leaning on the wire pulls the
        // voltage down for seconds at a time.
        live(voltage = "4200 V")
        val pending = check(AlarmState(), NOON)

        live(voltage = "5900 V")
        val cleared = check(pending, NOON.plusSeconds(60))

        verify(dataAccess, never()).commandItem(eq(MESSAGE_ITEM), any())
        assertThat(cleared.phase).isEqualTo(AlarmState.Phase.PENDING)
        assertThat(cleared.falseSince).isEqualTo(NOON.plusSeconds(60))
    }

    @Test
    fun `it nags up to the configured count and then stops`() {
        live(voltage = "4200 V")
        var state = check(AlarmState(), NOON)
        state = check(state, NOON.plusSeconds(120))

        // Too soon for the second message.
        state = check(state, NOON.plusSeconds(600))
        assertThat(state.sent).isEqualTo(1)

        state = check(state, NOON.plusSeconds(1320))
        assertThat(state.sent).isEqualTo(2)
        state = check(state, NOON.plusSeconds(2520))
        assertThat(state.sent).isEqualTo(3)
        assertThat(state.phase).isEqualTo(AlarmState.Phase.SILENT)

        // Still true an hour later, and it has said its piece.
        state = check(state, NOON.plusSeconds(6000))
        assertThat(state.sent).isEqualTo(3)
        verify(dataAccess, org.mockito.Mockito.times(3)).commandItem(eq(MESSAGE_ITEM), any())
    }

    @Test
    fun `it re-arms only after the conditions have been false long enough`() {
        live(voltage = "4200 V")
        var state = check(AlarmState(), NOON)
        state = check(state, NOON.plusSeconds(120))
        assertThat(state.sent).isEqualTo(1)

        live(voltage = "5900 V")
        state = check(state, NOON.plusSeconds(180))
        assertThat(state.phase).isEqualTo(AlarmState.Phase.FIRING)

        // A flap back under the threshold inside the hold-down keeps the alarm
        // where it was instead of starting over.
        live(voltage = "4200 V")
        state = check(state, NOON.plusSeconds(240))
        assertThat(state.sent).isEqualTo(1)

        live(voltage = "5900 V")
        state = check(state, NOON.plusSeconds(300))
        state = check(state, NOON.plusSeconds(1000))
        assertThat(state).isEqualTo(AlarmState())
        verify(dataAccess).commandItem(MESSAGE_ITEM, "CLEAR|$ALARM_ID|")
        verify(dataAccess).commandItem(MESSAGE_ITEM, "INFO|$ALARM_ID-ok|Horni ohradnik zase 5900 V")

        // Re-armed: the next fault notifies again.
        live(voltage = "4200 V")
        state = check(state, NOON.plusSeconds(1100))
        state = check(state, NOON.plusSeconds(1300))
        assertThat(state.sent).isEqualTo(1)
    }

    @Test
    fun `an item without a state freezes the alarm rather than clearing it`() {
        // The fence binding sets its channels to UNDEF when the cloud cannot be
        // reached. Treating that as "voltage fine" would withdraw a live alarm.
        live(voltage = "4200 V")
        var state = check(AlarmState(), NOON)
        state = check(state, NOON.plusSeconds(120))
        assertThat(state.sent).isEqualTo(1)

        live(voltage = "UNDEF")
        val frozen = check(state, NOON.plusSeconds(3000))

        assertThat(frozen).isEqualTo(state)
        verify(dataAccess, never()).commandItem(MESSAGE_ITEM, "CLEAR|$ALARM_ID|")
    }

    @Test
    fun `a condition that is definitely false wins over one that is unknown`() {
        // The lower energizer reads 0 V when it is switched off, so the alarm
        // hangs off its state item -- and "not RUNNING" settles the question
        // even while the voltage is unreadable.
        live(state = "STOPPED", voltage = "UNDEF")

        val result = check(AlarmState(), NOON)

        assertThat(result.phase).isEqualTo(AlarmState.Phase.CLEAR)
    }

    @Test
    fun `an INFO alarm is held during quiet hours and sent once they end`() {
        val quiet = Instant.parse("2026-09-15T21:30:00Z") // 23:30 in Prague
        live(voltage = "4200 V")

        var state = check(AlarmState(), quiet, severity = Severity.INFO)
        state = check(state, quiet.plusSeconds(120), severity = Severity.INFO)

        verify(dataAccess, never()).commandItem(eq(MESSAGE_ITEM), any())
        assertThat(state.sent).isEqualTo(0)

        val morning = Instant.parse("2026-09-16T05:30:00Z") // 07:30 in Prague
        state = check(state, morning, severity = Severity.INFO)

        assertThat(state.sent).isEqualTo(1)
    }

    @Test
    fun `an ALARM ignores quiet hours`() {
        val quiet = Instant.parse("2026-09-15T21:30:00Z")
        live(voltage = "4200 V")

        var state = check(AlarmState(), quiet)
        state = check(state, quiet.plusSeconds(120))

        assertThat(state.sent).isEqualTo(1)
    }

    private fun check(state: AlarmState, now: Instant, severity: Severity = Severity.ALARM): AlarmState {
        whenever(time.clock()).thenReturn(Clock.fixed(now, ZONE))
        return AlarmJob(dataAccess, config(severity), alarms(), time).check(state)
    }

    private fun live(state: String = "RUNNING", voltage: String) {
        mockItem(STATE_ITEM, state)
        mockItem(VOLTAGE_ITEM, voltage)
    }

    private fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    private fun alarms() = Alarms().apply {
        messageItem = MESSAGE_ITEM
        quietFromHour = 22
        quietToHour = 7
    }

    private fun config(severity: Severity) = AlarmJobConfig().apply {
        id = ALARM_ID
        message = "Horni ohradnik {$VOLTAGE_ITEM}"
        clearMessage = "Horni ohradnik zase {$VOLTAGE_ITEM}"
        this.severity = severity
        conditions = listOf(
            condition(STATE_ITEM, "RUNNING", Operator.EQ),
            condition(VOLTAGE_ITEM, "5000", Operator.LT)
        )
        delayMinutes = 2
        repeat = 3
        intervalMinutes = 20
        clearMinutes = 10
    }

    private fun condition(item: String, value: String, op: Operator) = Condition().apply {
        this.item = item
        this.value = value
        this.op = op
    }
}
