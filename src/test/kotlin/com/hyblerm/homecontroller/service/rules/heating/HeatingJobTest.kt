package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

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

    fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }
}
