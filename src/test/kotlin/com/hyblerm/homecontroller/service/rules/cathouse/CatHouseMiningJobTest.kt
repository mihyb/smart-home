package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRates
import com.hyblerm.homecontroller.service.repository.electricity.SpotElectricityPriceEvaluator
import com.hyblerm.homecontroller.service.repository.mining.L3IncomeProvider
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import java.lang.RuntimeException
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId

@SpringBootTest
@ActiveProfiles("test")
internal class CatHouseMiningJobTest {

    @MockBean
    lateinit var dataAccess: DataAccess

    @MockBean
    lateinit var electricityRateProvider: ElectricityRateProvider

    @MockBean
    lateinit var electricityPriceEvaluator: SpotElectricityPriceEvaluator

    @MockBean
    lateinit var time: Time

    @MockBean
    lateinit var l3IncomeProvider: L3IncomeProvider

    @Autowired
    lateinit var catHouseMiningJob: CatHouseMiningJob

    @BeforeEach
    fun setup() {
        whenever(time.clock()).thenReturn(Clock.systemDefaultZone())
        catHouseMiningJob.restartWaitPeriod = 0
        whenever(electricityPriceEvaluator.isElectricityFree()).thenReturn(false)
    }

    @Test
    fun `mining on if cats are freezing and mining is switched off`() {
        mockItem("TASMOTASWITCH8_TEMP", "-5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("minersocketzigbee_Power", "OFF")

        catHouseMiningJob.run()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "ON")
    }

    private fun mockCheapHours(currentHour: Int, from: Int, to: Int, maxHours: Int, prices: List<Double>) {
        whenever(time.clock()).thenReturn(Clock.fixed(OffsetDateTime.now().withHour(currentHour).toInstant(), ZoneId.systemDefault()))
        val priceMap = prices.mapIndexed { index, d -> index to d }.toMap()
        whenever(electricityRateProvider.getHourlyRates(any(), any())).thenReturn(ElectricityRates(priceMap))
        mockItem("Cat_Heating_From", from.toString())
        mockItem("Cat_Heating_To", to.toString())
        mockItem("Cat_Heating_Max_Hours", maxHours.toString())
    }

    @Test
    fun `mining on if electricity is cheap and temperature is low and mining is switched off`() {

        mockItem("TASMOTASWITCH8_TEMP", "3")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_MinTemp", "5")
        mockItem("minersocketzigbee_Power", "OFF")
        mockCheapHours(2, 0, 4, 2, listOf(2.0, 1.0, 1.0, 2.0))

        catHouseMiningJob.run()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "ON")
    }

    @Test
    fun `mining off if electricity is not cheap and temperature is low and mining is switched on`() {

        mockItem("TASMOTASWITCH8_TEMP", "3")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_MinTemp", "5")
        mockItem("minersocketzigbee_Power", "ON")
        mockCheapHours(2, 0, 4, 2, listOf(2.0, 5.0, 5.0, 2.0))

        catHouseMiningJob.run()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "OFF")
    }

    @Test
    fun `mining off if l3+ profitability detection fails`() {

        mockItem("TASMOTASWITCH8_TEMP", "15")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_MinTemp", "5")
        mockItem("minersocketzigbee_Power", "ON")
        mockCheapHours(5, 0, 4, 0, listOf(2.0, 0.0, 0.0, 0.0, 1.0, 1.0))
        whenever(l3IncomeProvider.getDailyIncomeUsd()).thenThrow(RuntimeException("failed"))

        catHouseMiningJob.run()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "OFF")
    }

    @Test
    fun `mining off if cat temp is ok and l3+ is not profitable`() {

        mockItem("TASMOTASWITCH8_TEMP", "15")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_MinTemp", "5")
        mockItem("minersocketzigbee_Power", "ON")
        mockCheapHours(5, 0, 4, 0, listOf(2.0, 0.0, 0.0, 0.0, 1.0, 1.0))
        whenever(l3IncomeProvider.getDailyIncomeUsd()).thenReturn(0.0)

        catHouseMiningJob.run()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "OFF")
    }

    @Test
    fun `mining kept if cats are freezing heating is switched on`() {
        mockItem("TASMOTASWITCH8_TEMP", "-5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("minersocketzigbee_Power", "ON")
        mockCheapHours(5, 0, 4, 0, listOf(2.0, 0.0, 0.0, 0.0, 1.0, 1.0))
        whenever(l3IncomeProvider.getDailyIncomeUsd()).thenReturn(0.0)

        catHouseMiningJob.run()

        verify(dataAccess, times(0)).commandItem(any(), any())
    }

    /*@Test
    fun `mining stopped if mining is on profitable but has wrong status`() {
        mockItem("TASMOTASWITCH8_TEMP", "25")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_MinTemp", "5")
        mockItem("minersocketzigbee_Power", "ON")
        mockItem("nicehashantminerstatus_Output", "OFFLINE")
        mockItem("nicehashantminerprofitability_Output", "5.0")
        whenever(l3IncomeProvider.getDailyIncomeUsd()).thenReturn(20.0)
        mockCheapHours(1, 0, 0, 0, listOf(0.0, 0.0, 0.0, 0.0, 0.0))

        catHouseMiningJob.runCheck()

        verify(dataAccess).commandItem("minersocketzigbee_Power", "OFF")
        verify(dataAccess).commandItem("minersocketzigbee_Power", "ON")
    }*/

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }
}
