package com.hyblerm.homecontroller.service.rules.common

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.BoilerModeJob
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class BoilerModeJobRunner(
    private val config: ConfigurationProperties,
    private val repository: DataAccess
) {

    private val logger: Logger = LoggerFactory.getLogger(BoilerModeJobRunner::class.java)

    /**
     * Five minutes is also the debounce: the boiler's water moves slowly enough
     * that a poll this slow cannot chase it, and neither the pump cycling nor a
     * fresh load going on shows up as anything but a trend.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun runBoilerModeJobs() {
        config.boilerModeJobs.forEach {
            logger.debug("Checking boiler modes against {}", it.temperatureItem)
            BoilerModeJob(repository, it).checkModes()
        }
    }
}
