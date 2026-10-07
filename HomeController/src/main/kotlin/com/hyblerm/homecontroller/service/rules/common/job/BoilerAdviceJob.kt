package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerAdviceJobConfig
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

private const val ADVICE_FIRE = "FIRE"
private const val ADVICE_ENOUGH = "ENOUGH"
private const val ADVICE_UNKNOWN = "UNKNOWN"

private const val STATE_ON = "ON"
private const val STATE_OFF = "OFF"

private const val ONE_DECIMAL = "%.1f"
private const val EMPTY_TANK_PERCENT = 0.0
private const val FULL_TANK_PERCENT = 100.0
private const val NO_SOLAR = 0.0
private const val HOURS_IN_A_DAY = 24

/**
 * openHAB renders a DateTime state as "2026-09-30T12:45:43.000+0200" -- an offset
 * with no colon in it, which ISO_OFFSET_DATE_TIME will not parse. Both spellings are
 * accepted here so that a change in openHAB's formatting cannot quietly stop the
 * staleness check.
 */
private val OPENHAB_TIMESTAMP: DateTimeFormatter = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
    .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
    .toFormatter()

/**
 * Says whether the accumulation tank holds enough to get through tomorrow.
 *
 * The boiler is loaded by hand and fired in one go -- the tank is charged to 75-100 %
 * because cycling a system this size is worse than filling it -- so the useful
 * question is never "when should it burn" but "will I have to light it tomorrow". A
 * day's warning is what makes wood, the cheap heat, available in time; without it the
 * house falls back on the electric panels, which cost more.
 *
 * Demand is a flat day's worth plus degree-days, less whatever the sun is expected to
 * bring. The flat part carries most of it and is not an allowance for hot water -- the
 * tank feeds the heating circuit alone, and hot water comes off the boiler while it
 * burns and off the electric boiler when it does not. It is there because the circuit
 * runs the regulator's weekly programme whenever it is in AUTO, so the load is mostly
 * the schedule: measured at 33 % of the tank a day against only 0.7 % per degree-day.
 * A model linear through zero instead predicted 13 % for a mild day that really costs
 * about 35, because nearly all of the demand sat in the intercept it did not have.
 *
 * The flat part is charged only on a day that needs heating at all. In summer the
 * circuit is off and the tank drains nothing, so a baseline applied regardless would
 * ask for wood in July. That leaves a cliff at the base temperature, which is blunt but
 * is the shape the regulator's own summer changeover has.
 *
 * Both numbers come from scripts/fit-buffer-drain.py and both are only as good as the
 * weather they were fitted over -- mild autumn, so far. Under-predicting asks for too
 * little wood, which is the expensive direction, so re-run it after the first cold
 * spell.
 *
 * Nothing is commanded but this job's own items. The advice is true all day; the
 * alert is raised only in the evening hours, when lighting a boiler is something a
 * person can still act on.
 */
open class BoilerAdviceJob(
    repository: DataAccess,
    private val config: BoilerAdviceJobConfig,
    private val time: Time
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger(BoilerAdviceJob::class.java)

    fun checkAdvice() {
        if (!item(config.statusItem).isOn()) {
            logger.debug("advisory is off")
            return
        }

        val needed = neededCharge()
        val charge = item(config.chargeItem).getQuantityOrNull()
        if (needed == null || charge == null) {
            // The numbers already standing in the output items are left alone: the
            // advice says UNKNOWN, which is the honest thing to read them next to.
            logger.warn("holding: tomorrow needs {} and the tank reads {}", needed, charge)
            publish(config.adviceItem, ADVICE_UNKNOWN)
            publish(config.alertItem, STATE_OFF)
            return
        }

        val shortfall = needed - charge
        val advice = if (shortfall > 0) ADVICE_FIRE else ADVICE_ENOUGH
        logger.debug("tomorrow wants {} %, tank has {} %, so {}", needed, charge, advice)

        publish(config.neededItem, ONE_DECIMAL.format(Locale.ROOT, needed))
        publish(config.shortfallItem, ONE_DECIMAL.format(Locale.ROOT, shortfall))
        publish(config.adviceItem, advice)
        publish(config.alertItem, if (advice == ADVICE_FIRE && withinAdviceHours()) STATE_ON else STATE_OFF)
    }

    /** How much of the tank tomorrow will want, or null when it cannot be said. */
    private fun neededCharge(): Double? {
        if (forecastIsStale()) {
            return null
        }
        val minimum = item(config.minTomorrowItem).getQuantityOrNull() ?: return null
        val maximum = item(config.maxTomorrowItem).getQuantityOrNull() ?: return null

        // Tomorrow's sun is the one input allowed to be missing. Without it the
        // advisory asks for the wood the day would need in cloud, and a tank that
        // turned out fuller than it had to be is the cheaper mistake.
        val solar = item(config.solarTomorrowItem).getQuantityOrNull()
        if (solar == null && config.chargePercentPerMegajoule > 0) {
            logger.warn("no value for {}, advising without the sun credit", config.solarTomorrowItem)
        }

        val degreeDays = (config.baseTemperatureCelsius - (minimum + maximum) / 2).coerceAtLeast(0.0)
        if (degreeDays <= 0) {
            // A day that needs no heating costs the tank nothing: the circuit is off and
            // the baseline is the schedule's load, not a standing charge.
            return EMPTY_TANK_PERCENT
        }
        val credit = (solar ?: NO_SOLAR) * config.chargePercentPerMegajoule
        val wanted = config.baselinePercentPerDay + degreeDays * config.chargePercentPerDegreeDay
        return (wanted - credit).coerceIn(EMPTY_TANK_PERCENT, FULL_TANK_PERCENT)
    }

    /**
     * A forecast that stopped arriving looks exactly like a current one: the last
     * numbers simply stand in the items. So the age is checked, and a forecast that
     * cannot be dated at all is treated as one that cannot be used -- which the
     * sitemap and an alarm on a lasting UNKNOWN both make visible.
     */
    private fun forecastIsStale(): Boolean {
        val state = item(config.forecastUpdatedItem).state
        val updated = try {
            OffsetDateTime.parse(state, OPENHAB_TIMESTAMP).toInstant()
        } catch (e: DateTimeParseException) {
            logger.warn("cannot read when the forecast arrived: {} reads {}", config.forecastUpdatedItem, state)
            return true
        }
        val age = Duration.between(updated, Instant.now(time.clock()))
        return age > Duration.ofHours(config.staleForecastHours)
    }

    private fun withinAdviceHours(): Boolean {
        val hour = LocalTime.now(time.clock()).hour
        return AdviceHours.contains(hour, config.adviseFromHour % HOURS_IN_A_DAY, config.adviseToHour % HOURS_IN_A_DAY)
    }
}
