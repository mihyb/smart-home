package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityPriceEvaluator
import com.hyblerm.homecontroller.service.rules.CalendarJob
import com.hyblerm.homecontroller.service.rules.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private const val SWITCH_ITEM_NAME = "hueplug2_Power"
private const val CALEDAR_ITEM = "TimelineTransferItem5"
@Service
class WaterHeaterJob(repository: DataAccess, val electricityPriceEvaluator: ElectricityPriceEvaluator) :
    CalendarJob(repository, "waterHeater_schedule", SWITCH_ITEM_NAME, CALEDAR_ITEM) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnPoolFiltration() {
        val switch = Switch(SWITCH_ITEM_NAME, repository)
        if (electricityPriceEvaluator.isElectricityFree() && !switch.isOn()) {
            switch.turnOn()
            logger.debug("Electricity price is negative. Turning on water heater.")
        } else {
            logger.debug("Electricity price is positive. Falling back to calendar setup.")
            super.checkSwitch()
        }
    }
}
