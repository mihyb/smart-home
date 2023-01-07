package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class CatHouseHeatingJob(
    val dataAccess: DataAccess,
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time
) : JobBase(dataAccess) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val temperatureItemId = "TASMOTASWITCH8_TEMP"
    val heatingFromItemId = "Cat_Heating_From"
    val heatingToItemId = "Cat_Heating_To"
    val maxHoursItemId = "Cat_Heating_Max_Hours"
    val heatingModeId = "Cat_heating_mode"
    val minTempId = "Cat_MinTemp"
    val freezeTempId = "Cat_FreezeTemp"

    @Scheduled(cron = "0 0 * ? * *")
    fun run() {

        val heaterSwitch = Switch("TASMOTASWITCH8_Status", dataAccess)
        if (item(heatingModeId).state == "OFF") {
            if (heaterSwitch.isOn()) {
                logger.debug("Heating is disabled. Turning heating off")
                heaterSwitch.turnOff()
            }
            return
        }

        if (item(temperatureItemId).getDouble() >= item(minTempId).getDouble()) {
            if (heaterSwitch.isOn()) {
                logger.debug("Temperature is ok. Turning heating off")
                heaterSwitch.turnOff()
            } else {
                logger.debug("Temperature is ok, heating skipped.")
            }
            return
        }

        if (item(temperatureItemId).getDouble() < item(freezeTempId).getDouble()) {
            logger.debug("Cathouse is freezing. Turning heating on")
            if (heaterSwitch.isOn()) {
                logger.debug("Cats are freezing heating is kept on")
            } else {
                heaterSwitch.turnOn()
                logger.debug("Cats are freezing heating turned on.")
            }
            return
        }

        val from = item(heatingFromItemId).getInt()
        val to = item(heatingToItemId).getInt()
        val hours = item(maxHoursItemId).getInt()
        val cheapHours = electricityRateProvider.getHourlyRates(OffsetDateTime.now())
            .getCheapestRates(hours, from, to)
            .keys
        logger.debug("Evaluating cathouse heating, cheap hours are: $cheapHours")

        val currenHour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour

        if (cheapHours.contains(currenHour) && !heaterSwitch.isOn()) {
            logger.debug("Turning heating on")
            heaterSwitch.turnOn()
        } else if (!cheapHours.contains(currenHour) && heaterSwitch.isOn()) {
            logger.debug("Turning heating off")
            heaterSwitch.turnOff()
        } else {
            logger.debug("No action")
        }
    }
}
