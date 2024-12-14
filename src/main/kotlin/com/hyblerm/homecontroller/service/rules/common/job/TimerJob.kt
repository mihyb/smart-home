package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.ZoneId

/**
 *
 *  TimerJob is a class that is used to switch an item on or off based on a start and end hour.
 *
 *  @param repository The repository used to access the items.
 *  @param time The time used to get the current hour.
 *  @param startHourItem The item that contains the start hour.
 *  @param endHourItem The item that contains the end hour.
 *  @param statusItem The item that contains the status of the timer.
 *  @param switchItem The item that will be switched on or off.
 */
open class TimerJob(
    repository: DataAccess,
    private val time: Time,
    private val startHourItem: String,
    private val endHourItem: String,
    private val statusItem: String,
    private val switchItem: String
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    fun checkSwitch() {
        logger.debug("data loaded")
        if (item(statusItem).isOn()) {
            logger.debug("timer is on")
            val hour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
            val switch = Switch(switchItem, repository)
            if (item(startHourItem).getDouble() > item(endHourItem).getDouble()) {
                checkHourCrossDay(hour, switch)
            } else {
                checkHourWithinDay(hour, switch)
            }
        }
    }

    private fun checkHourCrossDay(hour: Int, switch: Switch) {
        if (hour >= item(startHourItem).getDouble() || hour < item(endHourItem).getDouble()) {
            if (!switch.isOn()) {
                logger.info("switching on switch")
                switch.turnOn()
            }
        } else if (switch.isOn()) {
            logger.info("switching off switch")
            switch.turnOff()
        } else {
            logger.debug("switch is ok")
        }
    }

    private fun checkHourWithinDay(hour: Int, switch: Switch) {
        if (hour >= item(startHourItem).getDouble() && hour < item(endHourItem).getDouble()) {
            if (!switch.isOn()) {
                logger.info("switching on switch")
                switch.turnOn()
            }
        } else if (switch.isOn()) {
            logger.info("switching off switch")
            switch.turnOff()
        } else {
            logger.debug("switch is ok")
        }
    }
}
