package com.hyblerm.homecontroller.service.rules.insect

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class InsectLightJob(repository: DataAccess, time: Time) : TimerJob(repository, time, "Insect_timer_start", "Insect_timer_end", "Insect_timer_status", "Light_Bedroom") {

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    fun turnOnFishTankLight() {
        super.checkSwitch()
    }
}
