package com.hyblerm.homecontroller.service.rules.pool

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.IElectricityPriceEvaluator
import com.hyblerm.homecontroller.service.repository.solar.PowerPlant
import com.hyblerm.homecontroller.service.rules.CalendarJob
import com.hyblerm.homecontroller.service.rules.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private const val SWITCH_ITEM_NAME = "Pool_filtration"
private const val CALENDAR_ITEM_NAME = "TimelineTransferItem4"

@Service
class PoolFiltrationJob(repository: DataAccess, val electricityPriceEvaluator: IElectricityPriceEvaluator, val powerPlant: PowerPlant) :
    CalendarJob(repository, "Pool_filtration_calendar", SWITCH_ITEM_NAME, CALENDAR_ITEM_NAME) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnPoolFiltration() {
        val switch = Switch(SWITCH_ITEM_NAME, repository)
        if (electricityPriceEvaluator.isElectricityFree()) {
            if (!switch.isOn()) {
                switch.turnOn()
                logger.debug("Electricity price is negative. Turning on pool filtration.")
            }
        } else if (powerPlant.isSolarActiveAndCharged()) {
            if (!switch.isOn()) {
                switch.turnOn()
                logger.debug("Batteries are full. Turning on pool filtration.")
            }
        } else {
            logger.debug("Electricity price is positive. Falling back to calendar setup.")
            super.checkSwitch()
        }
    }
}
