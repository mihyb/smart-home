package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Service
class FishTankJob(val repository: DataAccess, val time: Time) {

    val logger = LoggerFactory.getLogger(FishTankJob::class.java)

    var fishTimerStartHour: OpenHabModel.Item = OpenHabModel.Item("", "Fish_timer_start", "")
    var fishTimerEndHour: OpenHabModel.Item = OpenHabModel.Item("", "Fish_timer_end", "")
    var fishTimerStatus: OpenHabModel.Item = OpenHabModel.Item("", "Fish_timer_status", "")
    var fishTankSwitch: OpenHabModel.Item = OpenHabModel.Item("", "mqtt_topic_dd50690b_TASMOTA_2", "")

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    fun turnOnFishTankLight() {
        init()
        logger.debug("data loaded")
        if (fishTimerStatus.isOn()) {
            logger.debug("timer is on")
            val hour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
            if (hour >= fishTimerStartHour.getDouble() && hour < fishTimerEndHour.getDouble()) {
                if (!fishTankSwitch.isOn()) {
                    logger.debug("switching on light")
                    repository.commandItem(fishTankSwitch.name, "ON")
                }
            } else if (fishTankSwitch.isOn()) {
                logger.debug("switching off light")
                repository.commandItem(fishTankSwitch.name, "OFF")
            } else {
                logger.debug("light is ok")
            }
        }
    }

    fun init() {
        fishTimerStartHour = repository.getItem(fishTimerStartHour.name)
        fishTimerEndHour = repository.getItem(fishTimerEndHour.name)
        fishTimerStatus = repository.getItem(fishTimerStatus.name)
        fishTankSwitch = repository.getItem(fishTankSwitch.name)
    }
}
