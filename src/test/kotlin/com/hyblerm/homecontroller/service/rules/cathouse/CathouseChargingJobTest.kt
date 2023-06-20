package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.stream.Stream

private const val SWITCH = "chargingcathouse_chargingcathouseswitch"

@SpringBootTest
@ActiveProfiles("test")
class CathouseChargingJobTest {

    @MockBean
    lateinit var dataAccess: DataAccess
    @MockBean
    lateinit var time: Time
    @MockBean
    lateinit var electricityRateProvider: ElectricityRateProvider

    @Autowired
    lateinit var cathouseChargingJob: CathouseChargingJob

    @ParameterizedTest
    @MethodSource("chargingDataProvider")
    fun `charging should turn on if solar is charged and active`(batteryDoc: Int, ppv: Int, switchStatus: String, switchExpected: String?) {
        mockItem("pp_battery_soc", batteryDoc.toString())
        mockItem("pp_ppv", ppv.toString())
        mockItem(SWITCH, switchStatus)
        whenever(electricityRateProvider.getBuyPriceCZK(any())).thenReturn(10.0)
        whenever(time.clock()).thenReturn(Clock.fixed(OffsetDateTime.parse("2011-12-03T04:00:30+01:00").toInstant(), ZoneId.systemDefault()))

        cathouseChargingJob.controlCharging()

        switchExpected?.let { command ->
            verify(dataAccess).commandItem(
                SWITCH,
                command
            )
        } ?: verify(dataAccess, times(0)).commandItem(any(), any())
    }

    @Test
    fun `charging should turn on if electricity price is negative`() {
        mockItem("pp_battery_soc", "0")
        mockItem("pp_ppv", "0")
        whenever(electricityRateProvider.getBuyPriceCZK(any())).thenReturn(-1.0)
        whenever(time.clock()).thenReturn(Clock.fixed(OffsetDateTime.parse("2011-12-03T04:00:30+01:00").toInstant(), ZoneId.systemDefault()))
        mockItem(SWITCH, "OFF")

        cathouseChargingJob.controlCharging()

        verify(dataAccess).commandItem(SWITCH, "ON")
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    companion object {

        @JvmStatic
        fun chargingDataProvider(): Stream<Arguments> {
            return Stream.of(
                // Arguments.of(100, 5000, "OFF", "ON"),
                Arguments.of(80, 5000, "OFF", null),
                Arguments.of(100, 1000, "OFF", null),
                Arguments.of(100, 5000, "ON", null),
                Arguments.of(80, 5000, "ON", "OFF"),
                Arguments.of(100, 500, "ON", "OFF")
            )
        }
    }
}
