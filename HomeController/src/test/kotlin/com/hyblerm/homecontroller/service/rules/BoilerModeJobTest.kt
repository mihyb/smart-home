package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerModeJobConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.BoilerModeJob
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val STATUS_ID = "boiler_auto_control"
private const val RUNNING_ID = "Atmos_Exhaust_Fan"
private const val HEATING_ID = "Atmos_C1_Mode"
private const val WATER_ID = "Atmos_Water_Mode"
private const val RUNNING_HEATING_ID = "boiler_auto_running_heating"
private const val RUNNING_WATER_ID = "boiler_auto_running_water"
private const val IDLE_HEATING_ID = "boiler_auto_idle_heating"
private const val IDLE_WATER_ID = "boiler_auto_idle_water"
private const val LAST_HEATING_ID = "boiler_auto_last_heating"
private const val LAST_WATER_ID = "boiler_auto_last_water"

class BoilerModeJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val job = BoilerModeJob(dataAccess, config())

    @BeforeEach
    fun `no record of an earlier cycle unless a test sets one`() {
        mockItem(LAST_HEATING_ID, "NULL")
        mockItem(LAST_WATER_ID, "NULL")
    }

    @Test
    fun `checkModes commands nothing when automatic control is off`() {
        mockItem(STATUS_ID, "OFF")
        mockItem(RUNNING_ID, "ON")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess, never()).commandItem(eq(WATER_ID), any())
    }

    @Test
    fun `checkModes keeps its record in step while off, so switching on is not read as a manual change`() {
        // Without this, a mode changed while the automation was off would look
        // like an override the moment it is switched back on, and it would turn
        // itself straight off again.
        mockItem(STATUS_ID, "OFF")
        mockItem(RUNNING_ID, "ON")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "AUTO")
        mockItem(LAST_HEATING_ID, "STANDBY")

        job.checkModes()

        verify(dataAccess).commandItem(LAST_HEATING_ID, "COMFORT")
        verify(dataAccess, never()).commandItem(eq(STATUS_ID), any())
    }

    @Test
    fun `checkModes skips when the boiler state is unknown`() {
        // Atmos_Exhaust_Fan reads NULL while the gateway is unreachable. Guessing
        // the boiler is idle would put the house into AUTO mid-burn.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "NULL")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "COMFORT")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess, never()).commandItem(eq(WATER_ID), any())
    }

    @Test
    fun `checkModes applies the modes chosen in the sitemap while the boiler burns`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "STANDBY")

        job.checkModes()

        verify(dataAccess).commandItem(HEATING_ID, "COMFORT")
        verify(dataAccess).commandItem(WATER_ID, "COMFORT")
    }

    @Test
    fun `checkModes applies the idle modes once the boiler is out`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "OFF")
        mockItem(IDLE_HEATING_ID, "AUTO")
        mockItem(IDLE_WATER_ID, "STANDBY")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "COMFORT")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess).commandItem(HEATING_ID, "AUTO")
        verify(dataAccess).commandItem(WATER_ID, "STANDBY")
    }

    @Test
    fun `checkModes records what it commanded, so the next cycle knows it was not a manual change`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess).commandItem(LAST_HEATING_ID, "COMFORT")
    }

    @Test
    fun `checkModes does not re-send a mode the circuit is already in`() {
        // The job runs every five minutes and the gateway has very few sockets.
        // Re-asserting a mode it already holds is how the binding once exhausted
        // them, so a no-op cycle must stay a no-op.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "COMFORT")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkModes hands control back when a circuit was changed underneath it`() {
        // Someone pressed a mode on the sitemap or on the controller's own panel.
        // Taking it back five minutes later is the behaviour this avoids.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AWAY")
        mockItem(WATER_ID, "COMFORT")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "COMFORT")

        job.checkModes()

        verify(dataAccess).commandItem(STATUS_ID, "OFF")
        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
    }

    @Test
    fun `checkModes stops commanding the other circuit too once it has handed control back`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AWAY")
        mockItem(WATER_ID, "AUTO")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess).commandItem(STATUS_ID, "OFF")
        verify(dataAccess, never()).commandItem(eq(WATER_ID), any())
    }

    @Test
    fun `checkModes does not hand control back on its first run, when it has no record`() {
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(STATUS_ID), any())
        verify(dataAccess).commandItem(HEATING_ID, "COMFORT")
    }

    @Test
    fun `checkModes does not read a change of target as a manual change`() {
        // The boiler went out, so the target moved from COMFORT to STANDBY. The
        // circuit is still where the job last left it, so nobody intervened.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "OFF")
        mockItem(IDLE_HEATING_ID, "STANDBY")
        mockItem(IDLE_WATER_ID, "STANDBY")
        mockItem(HEATING_ID, "COMFORT")
        mockItem(WATER_ID, "STANDBY")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "STANDBY")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(STATUS_ID), any())
        verify(dataAccess).commandItem(HEATING_ID, "STANDBY")
    }

    @Test
    fun `checkModes leaves a circuit alone when its own mode is unreadable`() {
        // One circuit reading NULL says nothing about the other, so the readable
        // one is still corrected. NULL is not a manual change either.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "COMFORT")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "NULL")
        mockItem(WATER_ID, "AUTO")
        mockItem(LAST_HEATING_ID, "COMFORT")
        mockItem(LAST_WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(STATUS_ID), any())
        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess).commandItem(WATER_ID, "COMFORT")
    }

    @Test
    fun `checkModes leaves a circuit alone until its target has been chosen`() {
        // The target items live only in persistence, so they read NULL on a
        // rebuilt machine until restoreOnStartup has run.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "NULL")
        mockItem(RUNNING_WATER_ID, "COMFORT")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess).commandItem(WATER_ID, "COMFORT")
    }

    @Test
    fun `checkModes refuses a target mode that expires on its own`() {
        // AWAY and VISIT end at a time of day and fall back to AUTO. As a target
        // the job would re-send them every five minutes for the rest of the day,
        // which is exactly the command storm the socket guard exists to avoid.
        mockItem(STATUS_ID, "ON")
        mockItem(RUNNING_ID, "ON")
        mockItem(RUNNING_HEATING_ID, "AWAY")
        mockItem(RUNNING_WATER_ID, "VISIT")
        mockItem(HEATING_ID, "AUTO")
        mockItem(WATER_ID, "AUTO")

        job.checkModes()

        verify(dataAccess, never()).commandItem(eq(HEATING_ID), any())
        verify(dataAccess, never()).commandItem(eq(WATER_ID), any())
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
        config.runningHeatingModeItem = RUNNING_HEATING_ID
        config.runningWaterModeItem = RUNNING_WATER_ID
        config.idleHeatingModeItem = IDLE_HEATING_ID
        config.idleWaterModeItem = IDLE_WATER_ID
        config.lastHeatingModeItem = LAST_HEATING_ID
        config.lastWaterModeItem = LAST_WATER_ID
        return config
    }
}
