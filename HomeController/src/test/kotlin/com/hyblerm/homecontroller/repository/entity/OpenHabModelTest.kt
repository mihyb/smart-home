package com.hyblerm.homecontroller.repository.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenHabModelTest {

    @Test
    fun `getQuantityOrNull reads a temperature that arrives with its unit`() {
        // Atmos_Boiler_Water is Number:Temperature, and the REST API renders a
        // QuantityType with the unit attached. getDoubleOrNull sees a string that
        // is not a number and returns null for every reading the item ever has.
        assertThat(item("86.5 °C").getQuantityOrNull()).isEqualTo(86.5)
        assertThat(item("86.5 °C").getDoubleOrNull()).isNull()
    }

    @Test
    fun `getQuantityOrNull reads a bare number too`() {
        assertThat(item("65").getQuantityOrNull()).isEqualTo(65.0)
        assertThat(item("-3.2 °C").getQuantityOrNull()).isEqualTo(-3.2)
    }

    @Test
    fun `getQuantityOrNull has no value when the item has none`() {
        assertThat(item("NULL").getQuantityOrNull()).isNull()
        assertThat(item("UNDEF").getQuantityOrNull()).isNull()
        assertThat(item("").getQuantityOrNull()).isNull()
    }

    private fun item(state: String) = OpenHabModel.Item("link", "Atmos_Boiler_Water", state)
}
