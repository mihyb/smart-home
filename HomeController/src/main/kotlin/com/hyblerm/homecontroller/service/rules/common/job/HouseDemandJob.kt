package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.HouseDemandJobConfig
import com.hyblerm.homecontroller.config.ConfigurationProperties.RoomConfig
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.Locale

private const val STATE_ON = "ON"
private const val STATE_OFF = "OFF"
private const val NO_ROOM = "-"
private const val ONE_DECIMAL = "%.1f"
private const val NO_GAP = 0.0

// How far ahead to look for the end of a room's day, and in what steps. Both thresholds
// that read it are well under this, so there is no point walking a whole day.
private const val OCCUPANCY_HORIZON_MINUTES = 360
private const val OCCUPANCY_STEP_MINUTES = 5

/**
 * Counts the rooms below the bottom of their band and decides whether that is the
 * boiler's problem.
 *
 * The boiler is loaded by hand and takes hours to bring the whole system up, so it
 * is not something to ask for because one room dipped. This job draws that line:
 * fewer than [HouseDemandJobConfig.fireAboveColdRooms] cold rooms is local work for
 * those rooms' own panels; at or above it, or as soon as a room with no panel is
 * cold, the answer is a fire.
 *
 * It commands nothing but its own outputs. The escalation leaves as
 * [HouseDemandJobConfig.alertItem] and the alarm table turns it into one message
 * about the house rather than one per room.
 *
 * The alert is deliberately narrow. It stays off while the tank still holds heat,
 * because that heat is already on its way into the rooms, and while the boiler is
 * burning, because a fire that has been lit but has not charged the tank yet would
 * otherwise read as a house nobody has done anything about.
 */
open class HouseDemandJob(
    repository: DataAccess,
    private val config: HouseDemandJobConfig,
    private val time: Time
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger(HouseDemandJob::class.java)

    fun assess(): HouseDemand {
        if (config.rooms.isEmpty()) {
            // Not configured at all, so there is no status item to read either.
            return HouseDemand.unknown()
        }
        if (!item(config.statusItem).isOn()) {
            logger.debug("advisory is off")
            return HouseDemand.unknown()
        }

        val cold = mutableListOf<ColdRoom>()
        val unreadable = mutableListOf<String>()
        val minutesLeft = mutableMapOf<String, Int>()
        config.rooms.forEach { room ->
            minutesLeft[room.name] = minutesUntilEmpty(room)
            if (minutesLeft.getValue(room.name) <= 0) {
                // Nobody is in it, so how cold it is does not matter yet. This is the
                // clearest saving in the whole advisory: the study is empty by late
                // afternoon and the kid is at school all weekday morning.
                return@forEach
            }
            when (val gap = gapBelowBand(room)) {
                null -> unreadable.add(room.name)
                else -> if (gap > config.toleranceKelvin) {
                    cold.add(ColdRoom(room.name, gap, room.localHeaterItem.isNotEmpty()))
                }
            }
        }

        val demand = HouseDemand(
            coldRooms = cold,
            unreadableRooms = unreadable,
            occupiedMinutesLeft = minutesLeft,
            wholeHouse = cold.size >= config.fireAboveColdRooms || cold.any { room -> !room.hasLocalHeater }
        )
        if (unreadable.isNotEmpty()) {
            logger.warn("no temperature or no band for {}", unreadable)
        }
        logger.debug(
            "{} cold room(s), {} empty, boiler's problem: {}",
            cold.size, demand.emptyRooms.size, demand.wholeHouse
        )
        publishDemand(demand)
        return demand
    }

    /**
     * Whether the room is in use at this hour, so its temperature is worth acting on.
     *
     * Occupancy is stated as the hours a room is *empty*, which is how the house is
     * actually known -- school, working hours, bedtime -- so a room with nothing
     * configured is always counted. That is the safe default: it asks for heat rather
     * than quietly going without.
     *
     * The night applies to every room and is configured once. A room's own windows are
     * on top of it.
     *
     * Note what this does *not* do: bring the heat forward so a room is warm when its
     * off-window ends. The regulator's own weekly programme does the morning ramp today.
     * Doing it here needs the room's time constant, which is what
     * scripts/fit-thermal-model.py is accumulating history for.
     */
    private fun occupied(room: RoomConfig, at: LocalDateTime): Boolean {
        if (AdviceHours.contains(at.hour, config.nightFromHour, config.nightToHour)) {
            return false
        }
        return room.offWindows.none { window ->
            window.mode.matches(at.toLocalDate()) &&
                AdviceHours.contains(at.hour, window.fromHour, window.toHour)
        }
    }

    /**
     * How many more minutes the room stays in use, 0 if it is already empty.
     *
     * Walked forward rather than solved, because a room can have several off-windows and
     * any of them can cross midnight; stepping is a few dozen comparisons and is obviously
     * correct, where the closed form would not be. Capped at the horizon, since every
     * threshold that reads this is in hours and only needs to know "at least that long".
     */
    private fun minutesUntilEmpty(room: RoomConfig): Int {
        val now = LocalDateTime.now(time.clock())
        if (!occupied(room, now)) {
            return 0
        }
        var minutes = OCCUPANCY_STEP_MINUTES
        while (minutes <= OCCUPANCY_HORIZON_MINUTES) {
            if (!occupied(room, now.plusMinutes(minutes.toLong()))) {
                return minutes - OCCUPANCY_STEP_MINUTES
            }
            minutes += OCCUPANCY_STEP_MINUTES
        }
        return OCCUPANCY_HORIZON_MINUTES
    }

    /**
     * How far the room is below its minimum, or null when it cannot be said.
     *
     * A room airing is cold for a reason and does not get to ask for a fire, so an
     * open window takes it out of the count rather than reporting it as warm --
     * which would be a claim nobody made.
     */
    private fun gapBelowBand(room: RoomConfig): Double? {
        if (room.windowItem.isNotEmpty() && item(room.windowItem).state == STATE_ON) {
            logger.debug("{} has a window open, not counting it", room.name)
            return null
        }
        // getQuantityOrNull reads both: the sensors are Number:Temperature and
        // arrive as "16.4 °C", the band items are plain Numbers.
        val temperature = item(room.temperatureItem).getQuantityOrNull() ?: return null
        val minimum = item(room.minItem).getQuantityOrNull() ?: return null
        return minimum - temperature
    }

    private fun publishDemand(demand: HouseDemand) {
        publish(config.coldRoomsItem, demand.coldRooms.size.toString())
        publish(config.coldestRoomItem, demand.coldest?.name ?: NO_ROOM)
        publish(config.coldestGapItem, ONE_DECIMAL.format(Locale.ROOT, demand.coldest?.gapKelvin ?: NO_GAP))
        publish(config.alertItem, if (worthLighting(demand)) STATE_ON else STATE_OFF)
    }

    private fun worthLighting(demand: HouseDemand): Boolean {
        if (!demand.wholeHouse) {
            return false
        }
        val charge = item(config.chargeItem).getQuantityOrNull()
        if (charge == null || charge > config.tankCoversAbovePercent) {
            logger.debug("not advising a fire: the tank reads {}", charge)
            return false
        }
        val burning = item(config.burningItem).state
        if (burning != STATE_OFF) {
            logger.debug("not advising a fire: {} reads {}", config.burningItem, burning)
            return false
        }
        return true
    }
}
