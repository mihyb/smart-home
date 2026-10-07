package com.hyblerm.homecontroller.service.rules.common

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.BoilerAdviceJob
import com.hyblerm.homecontroller.service.rules.common.job.HouseDemand
import com.hyblerm.homecontroller.service.rules.common.job.HouseDemandJob
import com.hyblerm.homecontroller.service.rules.common.job.PanelAdviceJob
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Runs the heating advisories. None of them switches anything.
 *
 * The house is assessed first and the verdict handed to the panel jobs as an
 * argument rather than through an item. It could be published and read back, but the
 * item cache holds a value for twenty seconds, so a panel job reading it in the same
 * cycle would get the previous one -- a bug that would only show on the day the
 * answer changed. The count is still published, for the sitemap.
 */
@Service
class AdviceJobRunner(
    private val config: ConfigurationProperties,
    private val repository: DataAccess,
    private val time: Time
) {

    private val logger: Logger = LoggerFactory.getLogger(AdviceJobRunner::class.java)

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun runAdviceJobs() {
        val demand = guarded("house demand") {
            HouseDemandJob(repository, config.houseDemand, time).assess()
        } ?: HouseDemand.unknown()

        config.panelAdviceJobs.forEach { panel ->
            guarded("panel advice for ${panel.switchItem}") {
                PanelAdviceJob(repository, panel, time).checkAdvice(demand)
            }
        }
        config.boilerAdviceJobs.forEach { boiler ->
            guarded("boiler advice") {
                BoilerAdviceJob(repository, boiler, time).checkAdvice()
            }
        }
    }

    /**
     * One misconfigured advisory must not take the others with it: getItem throws on
     * an item name that no longer exists, and an advisory is not worth a dead
     * scheduled task. Same reason AlarmJobRunner guards each alarm.
     */
    private fun <T> guarded(what: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: RuntimeException) {
            logger.error("{} could not be evaluated", what, e)
            null
        }
    }
}
