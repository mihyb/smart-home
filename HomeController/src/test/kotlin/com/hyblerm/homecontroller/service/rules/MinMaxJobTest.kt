package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.MinMaxJobConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.MinMaxJob
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

private const val SWITCH_ID = "switch"
private const val STATUS_ID = "status"
private const val VALUE_ID = "value"
private const val MIN_ID = "min"
private const val MAX_ID = "max"

class MinMaxJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val job = MinMaxJob(dataAccess, config())

    @Test
    fun `checkSwitch does nothing when disabled`() {
        mockItem(STATUS_ID, "OFF")

        job.checkSwitch()

        verify(dataAccess).getItem(STATUS_ID)
        verifyNoMoreInteractions(dataAccess)
    }

    @Test
    fun `checkSwitch turns on when value is below min`() {
        mockItem(STATUS_ID, "ON")
        mockItem(VALUE_ID, "18.0")
        mockItem(MIN_ID, "20.0")
        mockItem(MAX_ID, "25.0")
        mockItem(SWITCH_ID, "OFF")

        job.checkSwitch()

        verify(dataAccess).commandItem(SWITCH_ID, "ON")
    }

    @Test
    fun `checkSwitch turns off when value is above max`() {
        mockItem(STATUS_ID, "ON")
        mockItem(VALUE_ID, "27.0")
        mockItem(MIN_ID, "20.0")
        mockItem(MAX_ID, "25.0")
        mockItem(SWITCH_ID, "ON")

        job.checkSwitch()

        verify(dataAccess).commandItem(SWITCH_ID, "OFF")
    }

    @Test
    fun `checkSwitch does not command switch when value is between boundaries`() {
        mockItem(STATUS_ID, "ON")
        mockItem(VALUE_ID, "22.0")
        mockItem(MIN_ID, "20.0")
        mockItem(MAX_ID, "25.0")

        job.checkSwitch()

        verify(dataAccess).getItem(STATUS_ID)
        verify(dataAccess).getItem(VALUE_ID)
        verify(dataAccess).getItem(MIN_ID)
        verify(dataAccess).getItem(MAX_ID)
        verifyNoMoreInteractions(dataAccess)
    }

    private fun mockItem(name: String, value: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, value))
    }

    private fun config() = MinMaxJobConfig().apply {
        switchItem = SWITCH_ID
        statusItem = STATUS_ID
        valueItem = VALUE_ID
        minValueItem = MIN_ID
        maxValueItem = MAX_ID
    }
}
