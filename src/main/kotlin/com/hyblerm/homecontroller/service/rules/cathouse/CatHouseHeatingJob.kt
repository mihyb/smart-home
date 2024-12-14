package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.IElectricityPriceEvaluator
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class CatHouseHeatingJob(
    val dataAccess: DataAccess,
    val electricityPriceEvaluator: IElectricityPriceEvaluator,
) : JobBase(dataAccess) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val temperatureItemId = "TASMOTASWITCH8_TEMP"
    val heatingModeId = "Cat_heating_mode"
    val freezeTempId = "Cat_FreezeTemp"

    @Scheduled(cron = "0 1/31 * ? * *")
    fun run() {
        processHeating()
    }

    private fun processHeating() {
        val heaterSwitch = Switch("TASMOTASWITCH8_Status", dataAccess)
        if (item(heatingModeId).state == "OFF" && heaterSwitch.isOn()) {
            heaterSwitch.turnOff()
            logger.debug("Turning off cats heating. Heating is disabled.")
            return
        }

        val currentTemp = item(temperatureItemId).getDouble()
        val requestedTemp = item(freezeTempId).getDouble()

        if (electricityPriceEvaluator.isElectricityFree() && currentTemp < 25) {
            if (!heaterSwitch.isOn()) {
                heaterSwitch.turnOn()
                logger.debug("Turning on cats heating. Electricity is free.")
            }
            return
        }

        if (currentTemp < requestedTemp) {
            if (!heaterSwitch.isOn()) {
                heaterSwitch.turnOn()
                logger.debug("Turning on cats heating. Temp: $currentTemp requested: $requestedTemp")
            }
        } else {
            heaterSwitch.turnOff()
            logger.debug("Turning off cats heating. Temp: $currentTemp requested: $requestedTemp")
        }
    }
}
