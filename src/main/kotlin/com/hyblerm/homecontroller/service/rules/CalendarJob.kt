package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.items.CalendarItem
import com.hyblerm.homecontroller.service.rules.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
open class CalendarJob(
    repository: DataAccess,
    private val calendarStatus: String,
    private val switchItem: String,
    private val calendarItem: String?
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    fun checkSwitch() {
        val calendarEnabled = calendarItem?.let { item -> CalendarItem(item, repository) }
            .let { calendar -> calendar?.isActive() }
        if (calendarEnabled == false) {
            logger.debug("Calendar is disabled, skipping evaluation.")
            return
        }
        val switch = Switch(switchItem, repository)
        val status = item(calendarStatus)
        logger.debug("Checking switch: ${switch.isOn()} with status: $status")
        if (status.isOn() && !switch.isOn()) {
            logger.debug("Turning switch on")
            switch.turnOn()
        } else if (!status.isOn() && switch.isOn()) {
            logger.debug("Turning switch off")
            switch.turnOff()
        } else {
            logger.debug("Keeping switch as it is")
        }
    }
}
