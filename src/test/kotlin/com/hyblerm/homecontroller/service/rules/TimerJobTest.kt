package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.stream.Stream

private const val SWITCH_ID = "switch"

private const val STATUS_ID = "status"

private const val END_HOUR_ID = "endHour"

private const val START_HOUR_ID = "startHour"

class TimerJobTest {

    private val dataAccess = Mockito.mock(DataAccess::class.java)
    private val time = Mockito.mock(Time::class.java)
    var timerJob: TimerJob = TimerJob(dataAccess, time, timerJobConfig())

    @Test
    fun `checkSwitch should do nothing if it is disabled`() {
        mockItem(STATUS_ID, "OFF")

        timerJob.checkSwitch()

        verify(dataAccess).getItem(STATUS_ID)
        verifyNoMoreInteractions(dataAccess)
    }

    @ParameterizedTest
    @MethodSource("validTimeProvider")
    fun `checkSwitch should turn item on if it is inside of active hours`(hour: Int, minute: Int, startHour: String, endHour: String) {
        mockItem(STATUS_ID, "ON")
        mockItem(SWITCH_ID, "OFF")
        mockItem(START_HOUR_ID, startHour)
        mockItem(END_HOUR_ID, endHour)
        mockTime(hour, minute)

        timerJob.checkSwitch()

        verify(dataAccess).getItem(STATUS_ID)
        verify(dataAccess).commandItem(SWITCH_ID, "ON")
    }

    @ParameterizedTest
    @MethodSource("invalidTimeProvider")
    fun `checkSwitch should turn item off if it is inside of active hours`(hour: Int, minute: Int, startHour: String, endHour: String) {
        mockItem(STATUS_ID, "ON")
        mockItem(SWITCH_ID, "ON")
        mockItem(START_HOUR_ID, startHour)
        mockItem(END_HOUR_ID, endHour)
        mockTime(hour, minute)

        timerJob.checkSwitch()

        verify(dataAccess).getItem(STATUS_ID)
        verify(dataAccess).commandItem(SWITCH_ID, "OFF")
    }

    private fun mockTime(hour: Int, minute: Int) {
        val hours: String = if (hour < 10) "0$hour" else "$hour"
        val minutes: String = if (minute < 10) "0$minute" else "$minute"
        whenever(time.clock()).thenReturn(Clock.fixed(OffsetDateTime.parse("2011-12-03T$hours:$minutes:30+01:00").toInstant(), ZoneId.systemDefault()))
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    companion object {
        @JvmStatic
        fun validTimeProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(23, 30, "22", "3"),
                Arguments.of(15, 30, "10", "16"),
            )
        }

        @JvmStatic
        fun invalidTimeProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(4, 30, "22", "3"),
                Arguments.of(9, 30, "10", "16"),
            )
        }
    }

    private fun timerJobConfig(): ConfigurationProperties.TimerJobConfig {
        val timerJobConfig = ConfigurationProperties.TimerJobConfig()
        timerJobConfig.switchItem = SWITCH_ID
        timerJobConfig.statusItem = STATUS_ID
        timerJobConfig.startHourItem = START_HOUR_ID
        timerJobConfig.endHourItem = END_HOUR_ID
        return timerJobConfig
    }
}
