package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerMode
import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerModeJobConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.BoilerModeJob
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

private const val STATUS_ID = "boiler_auto_control"
private const val RUNNING_ID = "Atmos_Exhaust_Fan"
private const val HEATING_ID = "Atmos_C1_Mode"
private const val WATER_ID = "Atmos_Water_Mode"

class BoilerModeJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val job = BoilerModeJob(dataAccess, config())

    @Test
    fun `checkModes does nothing when automatic control is off`() {
        mockItem(STATUS_ID, "OFF")

        job.checkModes()

        verify(dataAccess).getItem(STATUS_ID)
        verifyNoMoreInteractions(dataAccess)
    }

    @Test
    fun `checkModes skips when the boiler state is unknown`() {
        // Atmos_Exhaust_Fan reads NULL while the gateway is unreachable. Guessing
        // the boiler is idle would put the house into AUTO mid-burn.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "NULL")

        job.checkModes()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkModes puts both circuits into comfort while the boiler burns`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "STANDBY")

        job.checkModes()

        verify(dataAccess).commandItem(HEATING_ID, "COMFORT")
        verify(dataAccess).commandItem(WATER_ID, "COMFORT")
    }

    @Test
    fun `checkModes releases heating to auto and stops hot water once the boiler is out`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "OFF")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess).commandItem(HEATING_ID, "AUTO")
        verify(dataAccess).commandItem(WATER_ID, "STANDBY")
    }

    @Test
    fun `checkModes does not re-send a mode the circuit is already in`() {
        // The job runs every five minutes and the gateway has very few sockets.
        // Re-asserting a mode it already holds is how the binding once exhausted
        // them, so a no-op cycle must stay a no-op.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkModes leaves a circuit alone when its own mode is unreadable`() {
        // One circuit reading NULL says nothing about the other, so the readable
        // one is still corrected.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(HEATING_ID, "NULL")
        mockItem(WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess).commandItem(WATER_ID, "COMFORT")
    }

    private fun mockItem(name: String, state: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, state))
    }

    private fun config(): BoilerModeJobConfig {
        val config = BoilerModeJobConfig()
        config.statusItem = STATUS_ID
        config.runningItem = RUNNING_ID
        config.heatingModeItem = HEATING_ID
        config.waterModeItem = WATER_ID
        config.runningHeatingMode = BoilerMode.COMFORT
        config.runningWaterMode = BoilerMode.COMFORT
        config.idleHeatingMode = BoilerMode.AUTO
        config.idleWaterMode = BoilerMode.STANDBY
        return config
    }
}
