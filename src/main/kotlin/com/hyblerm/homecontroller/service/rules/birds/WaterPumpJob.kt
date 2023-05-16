package com.hyblerm.homecontroller.service.rules.birds

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class WaterPumpJob(repository: DataAccess, time: Time) : TimerJob(repository, time, "Bird_waterpump_from", "Bird_waterpump_to", "Bird_waterpump_enabled", "waterpump_PowerState") {

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    fun turnOnWaterPump() {
        super.checkSwitch()
    }
}
