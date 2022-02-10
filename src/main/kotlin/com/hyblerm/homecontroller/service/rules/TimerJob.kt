package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.ZoneId

open class TimerJob(repository: DataAccess, private val time: Time, private val startHourItem: String, private val endHourItem: String, private val statusItem: String, private val switchItem: String) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    fun checkSwitch() {
        logger.debug("data loaded")
        if (item(statusItem).isOn()) {
            logger.debug("timer is on")
            val hour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
            val switch = Switch(switchItem, repository)
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
}
