package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.stream.Stream

internal class ApplianceTest {

    val tempId = "tempId"
    val commId = "commId"

    val commandItem = Mockito.mock(OpenHabModel.Item::class.java)
    val dataAccess: DataAccess = Mockito.mock(DataAccess::class.java)

    @BeforeEach
    fun setup() {
        whenever(dataAccess.getItem(commId)).thenReturn(commandItem)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun keepTemperature_shouldDoNothing_ifThereIsTemperatureConflict(status: Boolean) {
        whenever(commandItem.isOn()).thenReturn(status)
        val appliance = appliance(
            listOf(
                room(23.8, 0, 24.0),
                room(25.3, 0, 24.0)
            ),
            0.0,
            0.0
        )

        appliance.keepTemperature(Appliance.WorkingMode.HEAT)

        verifyNoInteractions(dataAccess)
    }

    @Test
    fun keepTemperature_shouldNotCool_ifApplianceDoesntSupportCooling() {
        val appliance = appliance(
            listOf(
                room(25.0, 0, 20.0)
            ),
            0.0,
            0.0,
            false
        )

        appliance.keepTemperature(Appliance.WorkingMode.COOL)

        verifyNoInteractions(dataAccess)
    }

    @ParameterizedTest
    @MethodSource("coolingTempProvider")
    fun keepTemperature_shouldOperateCooling_basedOnTemperature(
        roomTemp: Double,
        deviance: Int,
        requestedTemp: Double,
        lowerLimit: Double,
        upperLimit: Double,
        isOn: Boolean,
        expectedTemp: Double?,
        expectedState: String?
    ) {
        val appliance = appliance(
            listOf(
                room(roomTemp, deviance, requestedTemp)
            ),
            lowerLimit,
            upperLimit,
            true
        )
        whenever(commandItem.isOn()).thenReturn(isOn)

        appliance.keepTemperature(Appliance.WorkingMode.COOL)

        expectedTemp?.let { temp -> verify(dataAccess).commandItem(tempId, temp.toString()) }
        expectedState?.let { status -> verify(dataAccess).commandItem(commId, status) }
        if (roomTemp != requestedTemp + deviance) {
            verify(dataAccess).getItem(commId)
        }
        verifyNoMoreInteractions(dataAccess)
    }

    @ParameterizedTest
    @MethodSource("heatingTempProvider")
    fun keepTemperature_shouldOperateHeating_basedOnTemperature(
        roomTemp: Double,
        deviance: Int,
        requestedTemp: Double,
        lowerLimit: Double,
        upperLimit: Double,
        isOn: Boolean,
        expectedTemp: Double?,
        expectedState: String?
    ) {
        val appliance = appliance(
            listOf(
                room(roomTemp, deviance, requestedTemp)
            ),
            lowerLimit,
            upperLimit
        )
        whenever(commandItem.isOn()).thenReturn(isOn)

        appliance.keepTemperature(Appliance.WorkingMode.HEAT)

        verify(dataAccess).getItem(commId)
        expectedTemp?.let { temp -> verify(dataAccess).commandItem(tempId, temp.toString()) }
        expectedState?.let { status -> verify(dataAccess).commandItem(commId, status) }
        if (roomTemp != requestedTemp + deviance) {
            verify(dataAccess).getItem(commId)
        }
        verifyNoMoreInteractions(dataAccess)
    }

    private fun appliance(rooms: List<Room>, lowerLimit: Double, upperLimit: Double, supportsCooling: Boolean = false): Appliance {
        return Appliance(
            "test",
            rooms,
            Appliance.Limits(lowerLimit, upperLimit),
            Appliance.SwitchId(tempId, commId),
            dataAccess,
            supportsCooling
        )
    }

    private fun room(temp: Double, deviance: Int, requestedTemp: Double): Room {
        return Room("room", temp, deviance, requestedTemp)
    }

    companion object {
        @JvmStatic
        fun heatingTempProvider(): Stream<Arguments> {
            return Stream.of(
                // roomTemp, deviance, requestedTemp, lowerLimit, upperLimit, isOn, expectedTemp, expectedState
                Arguments.of(25, 0, 26, 0, 0, false, 26.0, "ON"), // state off + too cold -> ON
                Arguments.of(25, 0, 26, 0, 0, true, null, null), // state on + too cold -> skip
                Arguments.of(25, 0, 24, 0, 0, true, null, "OFF"), // state on + too hot -> OFF
                Arguments.of(25, 0, 24, 0, 0, false, null, null), // state off + too hot -> skip
                Arguments.of(25, 0, 25, 0, 0, true, null, null), // temp ok -> skip
                Arguments.of(25, 1, 25, 0, 0, false, 26.0, "ON"), // too hot because of room dev -> ON
                Arguments.of(25, -1, 25, 0, 0, true, null, "OFF"), // too cold because of room dev -> OFF
                Arguments.of(25, 0, 24, 0, 2, true, null, null), // room is hot but heating is kept ON because of appliance limits
                Arguments.of(24, 0, 25, 2, 0, false, null, null), // room is cold but heating is kept OFF because of appliance limits
                Arguments.of(28, 0, 24, 2, 0, true, null, "OFF"), // room is hot event with appliance limits -> TURN OFF
                Arguments.of(24, 0, 28, 0, 2, false, 28.0, "ON"), // room is cold event with appliance limits -> TURN ON
                Arguments.of(24, 0, 25, 0, 2, false, 25.0, "ON"), // room is cold with upper limit -> TURN ON
            )
        }

        @JvmStatic
        fun coolingTempProvider(): Stream<Arguments> {
            return Stream.of(
                // roomTemp, deviance, requestedTemp, lowerLimit, upperLimit, isOn, expectedTemp, expectedState
                Arguments.of(27, 0, 26, 0, 0, false, 26.0, "ON"), // state off + too hot -> ON
                Arguments.of(27, 0, 26, 0, 0, true, null, null), // state on + too hot -> skip
                Arguments.of(23, 0, 24, 0, 0, true, null, "OFF"), // state on + too cold -> OFF
                Arguments.of(23, 0, 24, 0, 0, false, null, null), // state off + too cold -> skip
                Arguments.of(25, 0, 25, 0, 0, true, null, null), // temp ok -> skip
                Arguments.of(25, -1, 25, 0, 0, false, 24.0, "ON"), // too hot because of room dev -> ON
                Arguments.of(25, 1, 25, 0, 0, true, null, "OFF"), // too cold because of room dev -> OFF
                Arguments.of(23, 0, 24, 0, 2, true, null, null), // room is cold but cooling is kept ON because of appliance limits
                Arguments.of(24, 0, 25, 2, 0, false, null, null), // room is hot but cooling is kept OFF because of appliance limits
                Arguments.of(19, 0, 24, 2, 0, true, null, "OFF"), // room is cold event with appliance limits -> TURN OFF
                Arguments.of(24, 0, 20, 0, 2, false, 20.0, "ON"), // room is hot event with appliance limits -> TURN ON
                Arguments.of(26, 0, 25, 0, 2, false, 25.0, "ON"), // room is hot with upper limit -> TURN ON
            )
        }
    }
}
