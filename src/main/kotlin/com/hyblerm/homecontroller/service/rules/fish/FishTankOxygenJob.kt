package com.hyblerm.homecontroller.service.rules.fish

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class FishTankOxygenJob(repository: DataAccess, time: Time) : TimerJob(repository, time, "Fish_oxygen_start", "Fish_oxygen_end", "Fish_oxygen_status", "fish_oxygen_switch") {

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnFishTankLight() {
        super.checkSwitch()
    }
}
