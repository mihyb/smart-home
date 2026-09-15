package com.hyblerm.homecontroller.service.rules.common

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.AlarmJob
import com.hyblerm.homecontroller.service.rules.common.job.AlarmState
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Runs the alarm table.
 *
 * Every minute rather than every five: a delay of two minutes cannot be honoured
 * by a five-minute cycle, and smoke and water are worth the extra polls. The
 * per-alarm state lives here because the jobs are rebuilt on every cycle.
 */
@Service
class AlarmJobRunner(
    private val config: ConfigurationProperties,
    private val repository: DataAccess,
    private val time: Time
) {

    private val logger: Logger = LoggerFactory.getLogger(AlarmJobRunner::class.java)
    private val states = ConcurrentHashMap<String, AlarmState>()

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    fun runAlarmJobs() {
        config.alarmJobs.forEach { alarm ->
            // One misconfigured alarm must not take the rest of the table with
            // it: getItem throws on an item name that no longer exists.
            try {
                val before = states[alarm.id] ?: AlarmState()
                states[alarm.id] = AlarmJob(repository, alarm, config.alarms, time).check(before)
            } catch (e: RuntimeException) {
                logger.error("alarm {} could not be evaluated", alarm.id, e)
            }
        }
    }
}
