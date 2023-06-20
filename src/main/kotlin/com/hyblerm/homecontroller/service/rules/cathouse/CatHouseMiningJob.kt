package com.hyblerm.homecontroller.service.rules.cathouse

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.repository.mining.L3IncomeProvider
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.MessageItem
import com.hyblerm.homecontroller.service.rules.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val HOURS_PER_DAY = 24
private const val MINER_CONSUMPTION_KWH = 0.8

@Service
class CatHouseMiningJob(
    val dataAccess: DataAccess,
    val electricityRateProvider: ElectricityRateProvider,
    val l3IncomeProvider: L3IncomeProvider,
    val time: Time,
    val configuration: ConfigurationProperties
) : JobBase(dataAccess) {

    var restartWaitPeriod = 30L

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    val temperatureItemId = "TASMOTASWITCH8_TEMP"
    val heatingFromItemId = "Cat_Heating_From"
    val heatingToItemId = "Cat_Heating_To"
    val maxHoursItemId = "Cat_Heating_Max_Hours"
    val freezeTempId = "Cat_FreezeTemp"
    val minTempId = "Cat_MinTemp"
    val antminerStatus = "nicehashantminerstatus_Output"
    val antminerProfitability = "nicehashantminerprofitability_Output"

    val messageItem = MessageItem(dataAccess)

    @Scheduled(cron = "0 0 * ? * *")
    fun run() {
        processMining()
    }

    @Scheduled(initialDelay = 5, timeUnit = TimeUnit.MINUTES, fixedRate = 5)
    fun runCheck() {
        val miningSwitch = Switch("minersocketzigbee_Power", dataAccess)
        if (miningSwitch.isOn()) {
            checkStatus(miningSwitch)
        }
    }

    private fun processMining() {
        val miningSwitch = Switch("minersocketzigbee_Power", dataAccess)
        val currentHour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour

        val currentTemp = item(temperatureItemId).getDouble()
        val freezeTemp = item(freezeTempId).getDouble()
        if (currentTemp < freezeTemp) {
            if (!miningSwitch.isOn()) {
                miningSwitch.turnOn()
                logger.debug("Turning on cats mining. Cats are freezing. Temp: $currentTemp freeze: $freezeTemp")
            }
            return
        }

        val requestedTemp = item(minTempId).getDouble()
        if (currentTemp < requestedTemp && isCheapHour(currentHour)) {
            if (!miningSwitch.isOn()) {
                miningSwitch.turnOn()
                logger.debug("Turning on cats mining. It is cheap hour. Temp: $currentTemp requestedTemp: $requestedTemp")
            }
            return
        }

        if (isMiningProfitable(currentHour)) {
            if (!miningSwitch.isOn()) {
                miningSwitch.turnOn()
                logger.debug("Turning on cats mining. Mining is profitable $$$.")
            }
        } else {
            miningSwitch.turnOff()
            logger.debug("Turning mining off. Temp: $currentTemp requested: $requestedTemp freeze: $freezeTemp")
        }
    }

    private fun checkStatus(miningSwitch: Switch) {
        val status = item(antminerStatus).state
        val profitability = item(antminerProfitability).getDouble()
        if (status == "OFFLINE" || profitability <= 0.0) {
            val message = "Rig is not working properly. Restarting. Status: $status profitability: $profitability"
            logger.debug(message)
            messageItem.command(message)
            miningSwitch.turnOff()
            TimeUnit.SECONDS.sleep(restartWaitPeriod)
            miningSwitch.turnOn()
        }
    }

    private fun isCheapHour(currenHour: Int): Boolean {
        val from = item(heatingFromItemId).getInt()
        val to = item(heatingToItemId).getInt()
        val hours = item(maxHoursItemId).getInt()
        val cheapHours = electricityRateProvider.getHourlyRates(OffsetDateTime.now(time.clock()))
            .getCheapestRates(hours, from, to)
            .keys
        logger.debug("Evaluating cathouse mining, cheap hours are: $cheapHours")
        return cheapHours.contains(currenHour)
    }

    fun isMiningProfitable(currentHour: Int): Boolean {
        try {
            val l3IncomeCzkKwh = l3IncomeProvider.getDailyIncomeUsd() * configuration.currency.usdRate / HOURS_PER_DAY / MINER_CONSUMPTION_KWH

            val currentElectricityRateKwh = electricityRateProvider.getBuyPriceCZK(currentHour)
            val isProfitable = currentElectricityRateKwh?.let { it < l3IncomeCzkKwh } ?: false
            logger.debug("Mining is profitable: $isProfitable income CZK/KWH: $l3IncomeCzkKwh electricity price: $currentElectricityRateKwh")
            return isProfitable
        } catch (e: Exception) {
            logger.error("Unable to resolve mining profitability.", e)
            return false
        }
    }
}
