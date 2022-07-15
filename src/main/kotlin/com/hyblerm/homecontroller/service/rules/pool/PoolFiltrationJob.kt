package com.hyblerm.homecontroller.service.rules.pool

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class PoolFiltrationJob(repository: DataAccess, time: Time) : TimerJob(repository, time, "Pool_on_from", "Pool_on_to", "Pool_filtration_timer", "Pool_filtration") {

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun turnOnFishTankLight() {
        super.checkSwitch()
    }
}
