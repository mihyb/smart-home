package com.hyblerm.homecontroller.service.rules.fish

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Service
class FishTankJob(repository: DataAccess, val time: Time) : JobBase(repository) {

    val logger = LoggerFactory.getLogger(FishTankJob::class.java)

    var fishTimerStartHour: String = "Fish_timer_start"
    var fishTimerEndHour: String = "Fish_timer_end"
    var fishTimerStatus: String = "Fish_timer_status"
    var fishTankSwitch: String = "mqtt_topic_dd50690b_TASMOTA_2"

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    fun turnOnFishTankLight() {
        logger.debug("data loaded")
        if (item(fishTimerStatus).isOn()) {
            logger.debug("timer is on")
            val hour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
            if (hour >= item(fishTimerStartHour).getDouble() && hour < item(fishTimerEndHour).getDouble()) {
                if (!item(fishTankSwitch).isOn()) {
                    logger.info("switching on light")
                    repository.commandItem(fishTankSwitch, "ON")
                }
            } else if (item(fishTankSwitch).isOn()) {
                logger.info("switching off light")
                repository.commandItem(fishTankSwitch, "OFF")
            } else {
                logger.debug("light is ok")
            }
        }
    }
}
