package com.hyblerm.homecontroller.service.rules.xmass

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.Switch
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// @Service
class XmassLightJob(repository: DataAccess, val time: Time) : JobBase(repository) {

    val fishTempItem = "mqtt_topic_1a4c7eb8_TASMOTA_7_SWITCH"

    val fromHour = 16
    val toHour = 22

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnXmassLight() {
        val item = Switch(fishTempItem, repository)
        if (isBefore(fromHour) || isAfter(toHour)) {
            if (item.isOn()) {
                item.turnOff()
            }
        } else {
            if (!item.isOn()) {
                item.turnOn()
            }
        }
    }

    private fun isAfter(fromHour: Int) = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour > fromHour
    private fun isBefore(fromHour: Int) = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour < fromHour
}
