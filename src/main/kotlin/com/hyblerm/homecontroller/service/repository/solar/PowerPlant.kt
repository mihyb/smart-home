package com.hyblerm.homecontroller.service.repository.solar

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.springframework.stereotype.Service

@Service
class PowerPlant(val repository: DataAccess) {

    val solarBatterySoc: String = "pp_battery_soc"
    val solarPanelProduction: String = "pp_ppv"

    fun isSolarActiveAndCharged(): Boolean {
        val batterySoc = item(solarBatterySoc).getInt()
        val solarProduction = item(solarPanelProduction).getInt()
        return batterySoc > 95 && solarProduction > 3000
    }

    fun item(name: String): OpenHabModel.Item {
        return repository.getItem(name)
    }
}
