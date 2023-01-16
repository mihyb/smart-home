package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.repository.mining.L3IncomeProvider
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
class CatHouseMiningJob(
    val dataAccess: DataAccess,
    val electricityRateProvider: ElectricityRateProvider,
    val l3IncomeProvider: L3IncomeProvider,
    val time: Time
) : JobBase(dataAccess) {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val temperatureItemId = "TASMOTASWITCH8_TEMP"
    val heatingFromItemId = "Cat_Heating_From"
    val heatingToItemId = "Cat_Heating_To"
    val maxHoursItemId = "Cat_Heating_Max_Hours"
    val freezeTempId = "Cat_FreezeTemp"
    val minTempId = "Cat_MinTemp"

    @Scheduled(cron = "0 0 * ? * *")
    fun run() {
        processMining()
    }

    private fun processMining() {

        val miningSwitch = Switch("minersocketzigbee_Power", dataAccess)

        val currentHour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour

        if (item(temperatureItemId).getDouble() < item(freezeTempId).getDouble() && !miningSwitch.isOn()) {
            miningSwitch.turnOn()
            return
        }

        if (!miningSwitch.isOn() && isCheapHour(currentHour) && item(temperatureItemId).getDouble() < item(minTempId).getDouble()) {
            miningSwitch.turnOn()
            return
        }

        if (!miningSwitch.isOn() && isMiningProfitable(currentHour)) {
            logger.debug("Turning heating on as mining is profitable")
            miningSwitch.turnOn()
        } else {
            miningSwitch.turnOff()
        }
    }

    private fun isCheapHour(currenHour: Int): Boolean {
        val from = item(heatingFromItemId).getInt()
        val to = item(heatingToItemId).getInt()
        val hours = item(maxHoursItemId).getInt()
        val cheapHours = electricityRateProvider.getHourlyRates(OffsetDateTime.now(time.clock()))
            .getCheapestRates(hours, from, to)
            .keys
        logger.debug("Evaluating cathouse heating, cheap hours are: $cheapHours")
        return cheapHours.contains(currenHour)
    }

    fun isMiningProfitable(currentHour: Int): Boolean {
        val l3DailyIncomeUsdKwh = l3IncomeProvider.getDailyIncomeUsd() * 22.0 /*TODO USD exchange rate*/ / 24 / 0.8

        val currentElectricityRateKwh = electricityRateProvider.getHourlyRates(OffsetDateTime.now(time.clock()))
            .getRate(currentHour, 24.0) // TODO EUR exchange rate
            ?.div(1000)
        return currentElectricityRateKwh?.let { it < l3DailyIncomeUsdKwh } ?: false
    }
}
