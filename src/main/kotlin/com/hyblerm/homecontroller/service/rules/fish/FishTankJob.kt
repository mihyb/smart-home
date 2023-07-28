package com.hyblerm.homecontroller.service.rules.fish

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.TimerJob
import com.hyblerm.homecontroller.service.rules.items.MessageItem
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class FishTankJob(repository: DataAccess, time: Time) : TimerJob(repository, time, "Fish_timer_start", "Fish_timer_end", "Fish_timer_status", "mqtt_topic_dd50690b_TASMOTA_2") {

    val messageItem = MessageItem(repository)
    val fishTempItem = "mqtt_topic_dd50690b_TASMOTA_2_TEMP"

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnFishTankLight() {
        super.checkSwitch()
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun checkTemp() {
        val fishTemp = item(fishTempItem).getInt()
        if (fishTemp > 28) {
            messageItem.command("Rybicky se pecou. Teplota: $fishTemp ˚C")
        }
    }
}
