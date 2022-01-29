package com.hyblerm.homecontroller.service.rules.heating

import org.springframework.stereotype.Component

@Component
class HeatingSystem(val appliances: List<Appliance>) {

    fun keepTemperature(mode: Appliance.WorkingMode) {
        appliances.forEach { it.keepTemperature(mode) }
    }
}
