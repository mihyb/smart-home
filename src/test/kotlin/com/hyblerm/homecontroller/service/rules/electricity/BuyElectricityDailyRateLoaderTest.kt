package com.hyblerm.homecontroller.service.rules.electricity

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.BuyElectricityDailyRateLoader
import com.hyblerm.homecontroller.repository.ElectricityRepository
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

internal class BuyElectricityDailyRateLoaderTest {

    private val electricityRepository: ElectricityRepository = Mockito.mock(ElectricityRepository::class.java)
    private val configurationProperties: ConfigurationProperties = Mockito.mock(ConfigurationProperties::class.java)
    private val rateLoader: ElectricityRateProvider = BuyElectricityDailyRateLoader(electricityRepository, configurationProperties, Time())

    @BeforeEach
    fun setup() {
        val electricity = ConfigurationProperties.Electricity()
        electricity.oteUrl = "https://www.ote-cr.cz/cs/kratkodobe-trhy/elektrina/denni-trh?date="
        whenever(configurationProperties.electricity).thenReturn(electricity)
        whenever(electricityRepository.findByHourTimeBetween(any(), any())).thenReturn(listOf())
    }

    @Test
    fun getHourlyRates_shouldPass() {
        val loadRates = rateLoader.getHourlyRates(OffsetDateTime.now())
        loadRates.getCheapestRates(24).forEach { println("hour ${it.key} price ${it.value}") }
    }
}
