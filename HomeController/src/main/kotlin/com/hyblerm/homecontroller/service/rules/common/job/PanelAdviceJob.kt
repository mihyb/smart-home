package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.PanelAdviceJobConfig
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalTime

private const val ADVICE_ON = "ON"
private const val ADVICE_OFF = "OFF"
private const val ADVICE_NONE = "NONE"
private const val ADVICE_BOILER = "BOILER"
private const val ADVICE_UNKNOWN = "UNKNOWN"

private const val STATE_ON = "ON"
private const val STATE_OFF = "OFF"

/**
 * Says whether one room's electric panel should be on. It never switches it.
 *
 * The panels are backup: electricity costs more than wood here, and they are
 * switched by hand when a single room is too cold to be worth a fire for. This job
 * exists to make that call visible before it is ever made automatic -- it writes
 * its opinion to [PanelAdviceJobConfig.adviceItem] and raises
 * [PanelAdviceJobConfig.alertItem] when the panel is not in the state it thinks it
 * should be, and the alarm table turns that into a message.
 * [PanelAdviceJobConfig.switchItem] is only ever read.
 *
 * Order of the reasons, cheapest heat first:
 *  - nobody is in the room, or not for long enough to pay for: the study is empty by
 *    late afternoon and the kid is at school, and switching a panel on shortly before a
 *    room empties buys nothing at all. A late *fire* is different and has no such rule,
 *    because it charges the tank for tomorrow rather than being spent on the hour
 *  - a window is open: nothing should be heating the outside
 *  - the boiler's circuit is delivering, or the tank still holds heat: wood is
 *    already on its way into the room and paying for electricity on top is waste
 *  - the house as a whole is cold: then the answer is a fire, and one message about
 *    the house beats one per room, so the advice is BOILER and the panel alert
 *    stays down
 *  - otherwise the room's own band decides, the same rule MinMaxJob follows
 *
 * Anything that cannot be read leaves the advice UNKNOWN rather than guessing. A
 * panel suggested because a sensor had dropped out is worse than no suggestion.
 */
open class PanelAdviceJob(
    repository: DataAccess,
    private val config: PanelAdviceJobConfig,
    private val time: Time
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger("${PanelAdviceJob::class.java.name}#${config.switchItem}")

    fun checkAdvice(demand: HouseDemand) {
        if (!item(config.statusItem).isOn()) {
            logger.debug("advisory is off")
            return
        }
        val advice = decide(demand)
        logger.debug("advice {}", advice)
        publish(config.adviceItem, advice)
        publish(config.alertItem, if (worthSaying(advice)) STATE_ON else STATE_OFF)
    }

    private fun decide(demand: HouseDemand): String {
        // Nobody in the room, or not for long enough to be worth paying for: a panel
        // switched on ten minutes before the room empties buys nothing. A null means the
        // house verdict says nothing about this room, and then this job decides alone.
        val minutesLeft = demand.minutesLeft(config.roomName)
        if (minutesLeft != null && minutesLeft < config.minimumRunMinutes) {
            logger.debug("{} has {} min of use left", config.roomName, minutesLeft)
            return ADVICE_OFF
        }
        if (config.windowItem.isNotEmpty()) {
            when (item(config.windowItem).state) {
                STATE_ON -> return ADVICE_OFF
                STATE_OFF -> Unit
                // A contact whose battery has died says nothing about the window,
                // and heating into an open one is the mistake worth avoiding.
                else -> return ADVICE_UNKNOWN
            }
        }

        val temperature = item(config.temperatureItem).getQuantityOrNull() ?: return ADVICE_UNKNOWN
        val minimum = item(config.minItem).getQuantityOrNull() ?: return ADVICE_UNKNOWN
        val maximum = item(config.maxItem).getQuantityOrNull() ?: return ADVICE_UNKNOWN
        val charge = item(config.chargeItem).getQuantityOrNull() ?: return ADVICE_UNKNOWN

        val pump = item(config.circuitPumpItem).state
        if (pump == STATE_ON || charge > config.tankCoversAbovePercent) {
            return ADVICE_OFF
        }
        // The pump cycles, so an off pump is not on its own proof the boiler is done
        // with the room -- but a pump that cannot be read is not proof of anything.
        if (pump != STATE_OFF) {
            logger.debug("{} reads {}", config.circuitPumpItem, pump)
            return ADVICE_UNKNOWN
        }

        return when {
            temperature < minimum -> if (demand.wholeHouse) ADVICE_BOILER else ADVICE_ON
            temperature > maximum -> ADVICE_OFF
            else -> ADVICE_NONE
        }
    }

    /**
     * Whether the advice is worth interrupting somebody with.
     *
     * Only a disagreement is: a panel already in the advised state needs no
     * message, and NONE, BOILER and UNKNOWN are not instructions. A panel left
     * running while the house waits for a fire is not corrected either -- it is
     * doing no harm, and nagging about it would cost more trust than it saves.
     *
     * A panel whose own state cannot be read is not a panel that is off. The study
     * panel speaks Tuya's local protocol and drops out of it, and isOn() reports a
     * NULL item as off -- which would turn a dropout into "switch on the heating" for
     * a panel that may already be running.
     */
    private fun worthSaying(advice: String): Boolean {
        val hour = LocalTime.now(time.clock()).hour
        if (!AdviceHours.contains(hour, config.adviseFromHour, config.adviseToHour)) {
            return false
        }
        return when (item(config.switchItem).state) {
            STATE_ON -> advice == ADVICE_OFF
            STATE_OFF -> advice == ADVICE_ON
            else -> false
        }
    }
}
