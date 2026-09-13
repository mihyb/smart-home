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
 * While it burns there is more heat than the buffer tank can hold and both
 * circuits should take it; once it is out, the hot water circuit would drain a
 * tank that has nothing refilling it, so it stops and the heating goes back to
 * its own schedule.
 *
 * Which mode each of those four cases means is read from openHAB rather than
 * from configuration, so it can be changed from the sitemap.
 *
 * Changing a mode by hand turns the automation off. The job records the mode it
 * left each circuit in; finding the circuit somewhere else means a person moved
 * it -- from the sitemap, or from the controller's own panel, where they have no
 * way of knowing an automation exists -- and quietly taking it back five minutes
 * later is worse than stopping. Switching [BoilerModeJobConfig.statusItem] back
 * on resumes control.
 */
open class BoilerModeJob(
    repository: DataAccess,
    private val config: BoilerModeJobConfig
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger("${BoilerModeJob::class.java.name}#${config.runningItem}")

    private class Circuit(val modeItem: String, val targetItem: String, val lastItem: String)

    fun checkModes() {
        var auto = item(config.statusItem).isOn()
        // NULL whenever the gateway is unreachable. Reading that as "not burning"
        // would drop the house into AUTO in the middle of a burn, so the job does
        // not command this cycle -- but it still watches for a manual change.
        val running = item(config.runningItem).state
        val known = running == STATE_ON || running == STATE_OFF
        if (auto && !known) {
            logger.warn("not commanding: {} reads {}", config.runningItem, running)
        }
        val burning = running == STATE_ON

        val circuits = listOf(
            Circuit(
                config.heatingModeItem,
                if (burning) config.runningHeatingModeItem else config.idleHeatingModeItem,
                config.lastHeatingModeItem
            ),
            Circuit(
                config.waterModeItem,
                if (burning) config.runningWaterModeItem else config.idleWaterModeItem,
                config.lastWaterModeItem
            )
        )

        for (circuit in circuits) {
            val current = item(circuit.modeItem).state
            val last = item(circuit.lastItem).state

            if (auto && movedByHand(last, current)) {
                logger.info(
                    "{} went from {} to {} outside this job - handing control back",
                    circuit.modeItem, last, current
                )
                repository.commandItem(config.statusItem, STATE_OFF)
                auto = false
            }
            if (!auto || !known) {
                remember(circuit.lastItem, last, current)
                continue
            }
            val target = target(circuit.targetItem)
            if (target == null || current == target.name) {
                if (target != null) logger.debug("{} already {}", circuit.modeItem, target)
                remember(circuit.lastItem, last, current)
                continue
            }
            // The gateway has very few sockets and commanding one that is not
            // answering achieves nothing. An unreadable mode means it is not
            // answering.
            if (mode(current) == null) {
                logger.warn("skipping {}: reads {}", circuit.modeItem, current)
                continue
            }
            logger.info("{}: {} -> {}", circuit.modeItem, current, target)
            repository.commandItem(circuit.modeItem, target.name)
            remember(circuit.lastItem, last, target.name)
        }
    }

    /**
     * True when the circuit is not where this job left it.
     *
     * Both states have to be real modes: no record yet means the job has never
     * run, and an unreadable mode means the gateway is down, neither of which is
     * somebody reaching for the panel.
     */
    private fun movedByHand(last: String, current: String): Boolean =
        mode(last) != null && mode(current) != null && last != current

    /** Records where the circuit stands, so the next cycle can tell what moved it. */
    private fun remember(lastItem: String, last: String, value: String) {
        if (mode(value) == null || last == value) {
            return
        }
        repository.commandItem(lastItem, value)
    }

    /** The mode chosen in the sitemap, or null when it cannot be used as one. */
    private fun target(targetItem: String): BoilerMode? {
        val state = item(targetItem).state
        val target = mode(state)
        if (target == null) {
            // Reads NULL until persistence restores it, or until it is set for
            // the first time on a rebuilt machine.
            logger.warn("skipping: {} reads {}", targetItem, state)
            return null
        }
        if (target.expires) {
            logger.warn("skipping: {} is {}, which ends by itself and cannot be a target", targetItem, target)
            return null
        }
        return target
    }

    private fun mode(state: String): BoilerMode? = BoilerMode.values().firstOrNull { it.name == state }
}
