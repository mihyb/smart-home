package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerMode
import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerModeJobConfig
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val STATE_ON = "ON"
private const val STATE_OFF = "OFF"

/**
 * Drives the Atmos heating circuit and hot water from whether the boiler burns.
 *
 * The boiler is loaded by hand, so nothing knows in advance when it will run.
 * While it burns there is more heat than the buffer tank can hold, and both
 * circuits should take it; once it is out, the hot water circuit would drain a
 * tank that has nothing refilling it, so it stops and the heating goes back to
 * its own schedule.
 *
 * This re-asserts the target mode on every cycle rather than only on a change,
 * which means a mode set by hand in the sitemap is taken back within one cycle.
 * [BoilerModeJobConfig.statusItem] is the way to turn that off.
 */
open class BoilerModeJob(
    repository: DataAccess,
    private val config: BoilerModeJobConfig
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger("${BoilerModeJob::class.java.name}#${config.runningItem}")

    fun checkModes() {
        if (!item(config.statusItem).isOn()) {
            logger.debug("automatic boiler control is off")
            return
        }
        // NULL whenever the gateway is unreachable. Reading that as "not burning"
        // would drop the house into AUTO in the middle of a burn, so wait for the
        // next cycle instead.
        val running = item(config.runningItem).state
        if (running != STATE_ON && running != STATE_OFF) {
            logger.warn("skipping: {} reads {}", config.runningItem, running)
            return
        }
        val burning = running == STATE_ON
        apply(config.heatingModeItem, if (burning) config.runningHeatingMode else config.idleHeatingMode)
        apply(config.waterModeItem, if (burning) config.runningWaterMode else config.idleWaterMode)
    }

    private fun apply(name: String, target: BoilerMode) {
        val current = item(name).state
        if (current == target.name) {
            logger.debug("{} already {}", name, target)
            return
        }
        // The gateway has very few sockets and commanding one that is not
        // answering achieves nothing. An unreadable mode means it is not
        // answering.
        if (BoilerMode.values().none { it.name == current }) {
            logger.warn("skipping {}: reads {}", name, current)
            return
        }
        logger.info("{}: {} -> {}", name, current, target)
        repository.commandItem(name, target.name)
    }
}
