package com.hyblerm.homecontroller.service.rules.heating

import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class RoomTest {

    @ParameterizedTest
    @MethodSource("roomProvider")
    fun roomShouldReturnCorrectRequestedAction(
        temp: Double,
        deviance: Int,
        requestedTemp: Double,
        expectedTemp: Double,
        expectedMode: Room.Mode
    ) {
        val room = Room("room", temp, deviance, requestedTemp)
        Assertions.assertThat(room.getRequestedAction()).isEqualTo(expectedMode)
        Assertions.assertThat(room.getRequestedTemp()).isEqualTo(expectedTemp)
    }

    companion object {
        @JvmStatic
        fun roomProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(25, 1, 27, 28, Room.Mode.HEAT),
                Arguments.of(25, 1, 20, 21, Room.Mode.COOL),
                Arguments.of(25, -1, 25, 24, Room.Mode.COOL),
                Arguments.of(25, 0, 25, 25, Room.Mode.KEEP)
            )
        }
    }
}
