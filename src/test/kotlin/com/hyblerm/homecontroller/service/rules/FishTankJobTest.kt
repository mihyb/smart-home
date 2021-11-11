package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.stream.Stream

internal class FishTankJobTest {
    private val dataAccess = Mockito.mock(DataAccess::class.java)
    private val time = Mockito.mock(Time::class.java)

    private val fishTankJob: FishTankJob = FishTankJob(dataAccess, time)

    @ParameterizedTest
    @MethodSource("timeProvider")
    fun test(from: Int, to: Int, currentHour: Int, currentMinute: Int, currentStatus: String, expectedCommand: String?) {
        mockItem("Fish_timer_start", "$from.0")
        mockItem("Fish_timer_end", "$to.0")
        mockItem("Fish_timer_status", "ON")
        mockItem("mqtt_topic_dd50690b_TASMOTA_2", currentStatus)

        mockTime(currentHour, currentMinute)

        fishTankJob.turnOnFishTankLight()

        if (expectedCommand != null) {
            verify(dataAccess).commandItem("mqtt_topic_dd50690b_TASMOTA_2", expectedCommand)
        } else {
            verify(dataAccess, Mockito.times(0)).commandItem(eq("mqtt_topic_dd50690b_TASMOTA_2"), any())
        }
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
        fun timeProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(8, 10, 9, 59, "OFF", "ON"),
                Arguments.of(8, 10, 9, 59, "ON", null),
                Arguments.of(8, 10, 10, 59, "OFF", null),
                Arguments.of(8, 10, 10, 59, "ON", "OFF"),
            )
        }
    }
}
