package com.hyblerm.homecontroller.service.rules.common

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.MinMaxJob
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class MinMaxJobRunner(
    private val config: ConfigurationProperties,
    private val repository: DataAccess
) {

    private val logger: Logger = LoggerFactory.getLogger(MinMaxJobRunner::class.java)

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun runMinMaxJobs() {
        config.minMaxJobs.forEach {
            logger.debug("Checking min/max for switch {}", it.switchItem)
            MinMaxJob(repository, it).checkSwitch()
        }
    }
}
