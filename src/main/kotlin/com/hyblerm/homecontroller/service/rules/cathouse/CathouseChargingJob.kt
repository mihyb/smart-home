package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityPriceEvaluator
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CathouseChargingJob(
    val dataAccess: DataAccess,
    val electricityPriceEvaluator: ElectricityPriceEvaluator,
    val time: Time
) : JobBase(dataAccess) {

    val solarBatterySoc: String = "pp_battery_soc"
    val solarPanelProduction: String = "pp_ppv"
    val chargingSwitch: String = "chargingcathouse_chargingcathouseswitch"

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun controlCharging() {
        val currentStatus = Switch(chargingSwitch, dataAccess)
        if (shouldCharge() && !currentStatus.isOn()) {
            currentStatus.turnOn()
        } else if (currentStatus.isOn() && !isSolarActiveAndCharged()) {
            currentStatus.turnOff()
        }
    }

    fun shouldCharge(): Boolean {
        return isSolarActiveAndCharged() || electricityPriceEvaluator.isElectricityFree()
    }

    fun isSolarActiveAndCharged(): Boolean {
        val batterySoc = item(solarBatterySoc).getInt()
        val solarProduction = item(solarPanelProduction).getInt()
        return batterySoc > 95 && solarProduction > 3000
    }
}
