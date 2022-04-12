package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.stream.Stream

internal class HeatingJobTest {

    private val dataAccess: DataAccess = Mockito.mock(DataAccess::class.java)
    private val heatingJob: HeatingJob = HeatingJob(dataAccess)

    @Test
    fun keepTemperature_ShouldDoNothing_ifHeatingIsOff() {
        mockItem("heating_main_switch", "OFF")

        heatingJob.keepTemperature()

        verify(dataAccess).getItem("heating_main_switch")
        verifyNoMoreInteractions(dataAccess)
    }

    @ParameterizedTest
    @MethodSource("houseLockProvider")
    fun isDayShouldReturnFalse_ifHouseIsLocked(name: String, delay: Int, isDayTime: Boolean, isHouseLocked: Boolean, isDay: Boolean) {
        val dayItemName = "dayItem"
        mockItem(dayItemName, if (isDayTime) "ON" else "OFF")
        val result = heatingJob.isDay(delay, dayItemName, isHouseLocked)
        Assertions.assertThat(result).isEqualTo(isDay)
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    companion object {
        @JvmStatic
        fun houseLockProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Is day is true during day time", 0, true, false, true),
                Arguments.of("Is day is false during day time if heating is delayed", 10, true, false, false),
                Arguments.of("Is day is false during day time if house is locked", 0, true, true, false),
                Arguments.of("Is day is false during night", 0, false, false, false)
            )
        }
    }
}
