package com.hyblerm.homecontroller.service.repository.electricity

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class ElectricityRatesTest {

    private val ratesMap: Map<Int, Double> = mapOf(
        Pair(1, 2.0),
        Pair(2, 2.0),
        Pair(3, 3.0),
        Pair(4, 4.0),
        Pair(5, 5.0),
        Pair(6, 5.0),
        Pair(7, 2.0),
        Pair(8, 1.0),
    )
    private val eRates: ElectricityRates = ElectricityRates(ratesMap)

    @ParameterizedTest
    @MethodSource("segmentsProvider")
    fun getCheapestRate_shouldReturnCorrectAmountOfCheapestRates(amount: Int, expected: Map<Int, Double>) {
        Assertions.assertThat(eRates.getCheapestRates(amount)).containsExactlyEntriesOf(expected)
    }

    @ParameterizedTest
    @MethodSource("segmentsFromToProvider")
    fun getCheapestRate_shouldReturnCorrectAmountOfCheapestRatesWithinRange(amount: Int, from: Int, to: Int, expected: Map<Int, Double>) {
        Assertions.assertThat(eRates.getCheapestRates(amount, from, to)).containsExactlyEntriesOf(expected)
    }

    @Test
    fun getRate_shouldReturnRate_forHourSpecified() {
        Assertions.assertThat(eRates.getRate(7)).isEqualTo(2.0)
    }

    companion object {

        @JvmStatic
        fun segmentsProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(1, mapOf(Pair(8, 1.0))),
                Arguments.of(2, mapOf(Pair(8, 1.0), Pair(1, 2.0))),
                Arguments.of(3, mapOf(Pair(8, 1.0), Pair(1, 2.0), Pair(2, 2.0)))
            )
        }

        @JvmStatic
        fun segmentsFromToProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(2, 3, 6, mapOf(Pair(3, 3.0), Pair(4, 4.0))),
            )
        }
    }
}
