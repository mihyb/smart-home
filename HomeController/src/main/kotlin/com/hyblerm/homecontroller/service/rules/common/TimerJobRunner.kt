package com.hyblerm.homecontroller.service.rules.common

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.TimerJob
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class TimerJobRunner(
    private val config: ConfigurationProperties,
    private val repository: DataAccess,
    private val time: Time
) {

    private val logger: Logger = LoggerFactory.getLogger(TimerJobRunner::class.java)

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun runTimerJobs() {
        config.timerJobs.forEach {
            logger.debug("Checking timer for switch {}", it.switchItem)
            TimerJob(repository, time, it).checkSwitch()
        }
    }
}
