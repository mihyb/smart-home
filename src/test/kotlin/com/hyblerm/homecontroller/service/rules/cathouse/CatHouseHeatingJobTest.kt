package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
internal class CatHouseHeatingJobTest {

    @MockBean
    lateinit var dataAccess: DataAccess

    @Autowired
    lateinit var catHouseHeatingJob: CatHouseHeatingJob

    @Test
    fun `heating on if cats are freezing and heating is enabled and heating is switched off`() {
        mockItem("TASMOTASWITCH8_TEMP", "-5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_heating_mode", "ON")
        mockItem("TASMOTASWITCH8_Status", "OFF")

        catHouseHeatingJob.run()

        verify(dataAccess).commandItem("TASMOTASWITCH8_Status", "ON")
    }

    @Test
    fun `heating off if cats are freezing and heating is disabled and heating is switched on`() {
        mockItem("TASMOTASWITCH8_TEMP", "-5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_heating_mode", "OFF")
        mockItem("TASMOTASWITCH8_Status", "ON")

        catHouseHeatingJob.run()

        verify(dataAccess).commandItem("TASMOTASWITCH8_Status", "OFF")
    }

    @Test
    fun `heating off if cats are not freezing and heating is enabled and heating is switched on`() {
        mockItem("TASMOTASWITCH8_TEMP", "5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_heating_mode", "ON")
        mockItem("TASMOTASWITCH8_Status", "ON")

        catHouseHeatingJob.run()

        verify(dataAccess).commandItem("TASMOTASWITCH8_Status", "OFF")
    }

    @Test
    fun `heating kept if cats are freezing and heating is enabled and heating is switched on`() {
        mockItem("TASMOTASWITCH8_TEMP", "-5")
        mockItem("Cat_FreezeTemp", "0")
        mockItem("Cat_heating_mode", "ON")
        mockItem("TASMOTASWITCH8_Status", "ON")

        catHouseHeatingJob.run()

        verify(dataAccess, times(0)).commandItem(any(), any())
    }

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }
}
