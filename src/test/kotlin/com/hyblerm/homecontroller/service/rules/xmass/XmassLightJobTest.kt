package com.hyblerm.homecontroller.service.rules.xmass

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.stream.Stream

private const val item = "mqtt_topic_1a4c7eb8_TASMOTA_7_SWITCH"

class XmassLightJobTest {

    private val dataAccess: DataAccess = Mockito.mock(DataAccess::class.java)
    private val time = Mockito.mock(Time::class.java)
    private val heatingJob: XmassLightJob = XmassLightJob(dataAccess, time)

    @ParameterizedTest
    @MethodSource("timeProvider")
    fun turnOnXmassLight_shouldTurnOff(currentState: String, hour: Int, minute: Int, expected: String?) {
        mockItem(item, currentState)
        mockTime(hour, minute)

        heatingJob.turnOnXmassLight()

        if (expected != null) {
            verify(dataAccess).commandItem(item, expected)
        } else {
            verify(dataAccess, never()).commandItem(any(), any())
        }
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    private fun mockTime(hour: Int, minute: Int) {
        val hours: String = if (hour < 10) "0$hour" else "$hour"
        val minutes: String = if (minute < 10) "0$minute" else "$minute"
        whenever(time.clock()).thenReturn(Clock.fixed(OffsetDateTime.parse("2011-12-03T$hours:$minutes:30+01:00").toInstant(), ZoneId.systemDefault()))
    }

    companion object {
        @JvmStatic
        fun timeProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("ON", 2, 0, "OFF"),
                Arguments.of("ON", 20, 0, null),
                Arguments.of("OFF", 2, 0, null),
                Arguments.of("OFF", 16, 0, "ON"),
            )
        }
    }
}
