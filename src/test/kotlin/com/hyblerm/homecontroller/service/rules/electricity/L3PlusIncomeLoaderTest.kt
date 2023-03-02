package com.hyblerm.homecontroller.service.rules.electricity

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.L3PlusIncomeLoader
import com.hyblerm.homecontroller.service.repository.mining.L3IncomeProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

internal class L3PlusIncomeLoaderTest {

    private val configurationProperties: ConfigurationProperties = Mockito.mock(ConfigurationProperties::class.java)
    private val l3IncomeProvider: L3IncomeProvider = L3PlusIncomeLoader(configurationProperties)

    @BeforeEach
    fun setup() {
        val nicehash = ConfigurationProperties.Nicehash()
        nicehash.l3PlusIncomeUrl = "https://www.asicminervalue.com/miners/bitmain/antminer-l3-504mh"
        whenever(configurationProperties.nicehash).thenReturn(nicehash)
    }

    @Test
    fun getDailyIncomeUsd_shouldPass() {
        val income = l3IncomeProvider.getDailyIncomeUsd()
        Assertions.assertThat(income).isNotNull
    }
}
