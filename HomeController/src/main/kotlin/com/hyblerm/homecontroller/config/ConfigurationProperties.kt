package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Hotter than residual heat in a boiler that has gone out, whatever the fan says. */
private const val DEFAULT_BURNING_ABOVE_CELSIUS = 85.0

/** Below this outdoor temperature the house needs heating; above it, it does not. */
private const val DEFAULT_BASE_TEMPERATURE_CELSIUS = 18.0

/**
 * What a day of heating costs the tank before the weather is counted at all.
 *
 * Measured, not assumed: scripts/fit-buffer-drain.py over eight days that started with
 * a full tank put it at 33 % a day with only 0.7 % per degree-day on top. The tank
 * feeds the heating circuit alone, so that looked like it should be pure weather -- but
 * the circuit runs the regulator's weekly programme whenever it is in AUTO, so most of
 * the load is the schedule rather than the outside temperature.
 */
private const val DEFAULT_BASELINE_PERCENT_PER_DAY = 33.0

/** How much of the tank each degree-day adds on top of the baseline. Measured with it. */
private const val DEFAULT_CHARGE_PERCENT_PER_DEGREE_DAY = 0.7

/** Older than this and the forecast is not something to advise from. */
private const val DEFAULT_STALE_FORECAST_HOURS = 3L

private const val DEFAULT_BOILER_ADVISE_FROM_HOUR = 16
private const val DEFAULT_BOILER_ADVISE_TO_HOUR = 22

/** How many rooms have to be cold at once before it is the boiler's problem. */
private const val DEFAULT_FIRE_ABOVE_COLD_ROOMS = 2

/** How far below its minimum a room sits before it counts as cold rather than resting on it. */
private const val DEFAULT_TOLERANCE_KELVIN = 0.3

/** The house is empty overnight; no room's temperature asks for a fire between these. */
private const val DEFAULT_NIGHT_FROM_HOUR = 21
private const val DEFAULT_NIGHT_TO_HOUR = 6

/** Above this the tank still has heat worth waiting for rather than paying for. */
private const val DEFAULT_TANK_COVERS_ABOVE_PERCENT = 40.0

/** Too little of the room's day left for a panel to be worth switching on. */
private const val DEFAULT_MINIMUM_RUN_MINUTES = 45

private const val DEFAULT_PANEL_ADVISE_FROM_HOUR = 6
private const val DEFAULT_PANEL_ADVISE_TO_HOUR = 22

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var timerJobs = mutableListOf<TimerJobConfig>()
    var minMaxJobs = mutableListOf<MinMaxJobConfig>()
    var boilerModeJobs = mutableListOf<BoilerModeJobConfig>()
    var boilerAdviceJobs = mutableListOf<BoilerAdviceJobConfig>()
    // One house, so one entry rather than a list -- the same shape as [alarms].
    var houseDemand = HouseDemandJobConfig()
    var panelAdviceJobs = mutableListOf<PanelAdviceJobConfig>()
    var alarms = Alarms()
    var alarmJobs = mutableListOf<AlarmJobConfig>()

    class Electricity {
        var oteUrl = ""
        var buy = ElectricityPrice()
        var sell = ElectricityPrice()
    }

    class ElectricityPrice {
        var fixedPriceKwh = 0.0
    }

    class OpenHab {
        var baseUrl: String = ""
        var itemsRelativePath: String = "items"
        var itemRelativePath: String = "items/{name}"
    }

    class TimerJobConfig {
        var name: String = ""
        var switchItem: String = ""
        var statusItem: String = ""
        var startHourItem: String = ""
        var endHourItem: String = ""
        var mode: Mode = Mode.ALL
        var conditions: List<Condition> = emptyList()

        enum class Mode {
            ALL, WEEKDAY, WEEKEND
        }
    }

    class Condition {
        var item: String = ""
        var value: String = ""

        /**
         * How [value] is compared with the item's state. Equality is the default
         * because it is all the timer jobs ever needed; the ordering operators
         * exist for alarms, which watch a measurement rather than a switch.
         */
        var op: Operator = Operator.EQ
    }

    /**
     * UNDEF and DEFINED ask whether openHAB has a state at all, which no other
     * operator can answer: every one of them reports "unknown" for an item
     * whose thing is offline, so an alarm built from them goes quiet exactly
     * when the house has stopped reporting.
     */
    enum class Operator {
        EQ, NE, LT, LTE, GT, GTE, UNDEF, DEFINED
    }

    /**
     * What a notification is allowed to interrupt.
     *
     * INFO is held during quiet hours rather than dropped: the condition is
     * still true, so the first cycle after they end sends it. ALARM ignores
     * them -- a fence that went dead at two in the morning is dead all night.
     */
    enum class Severity {
        INFO, ALARM
    }

    class Alarms {
        // The openHAB item that carries a notification to the rule that pushes
        // it. Nothing here knows how a push is delivered, which is the point:
        // sendNotification exists only inside openHAB.
        var messageItem: String = ""
        var quietFromHour: Int = 22
        var quietToHour: Int = 7
    }

    /**
     * One watched condition, plus how often it may interrupt you.
     *
     * The state machine is CLEAR -> PENDING -> FIRING -> SILENT and back:
     * [conditions] have to hold for [delayMinutes] before the first message, at
     * most [repeat] messages go out [intervalMinutes] apart, and the alarm
     * re-arms only once the conditions have been false continuously for
     * [clearMinutes]. That last one is what stops a measurement sitting on its
     * threshold from notifying on every flap.
     *
     * An item with no state freezes the machine instead of clearing it --
     * "the fence voltage is unknown" is not "the fence voltage is fine".
     */
    class AlarmJobConfig {
        var id: String = ""
        var message: String = ""

        // Sent once the alarm re-arms, if set. Empty means the notification is
        // only withdrawn from the phone, without a second push.
        var clearMessage: String = ""
        var severity: Severity = Severity.INFO
        var conditions: List<Condition> = emptyList()
        var delayMinutes: Long = 0
        var repeat: Int = 1
        var intervalMinutes: Long = 30
        var clearMinutes: Long = 5
    }

    class MinMaxJobConfig {
        var switchItem: String = ""
        var statusItem: String = ""
        var valueItem: String = ""
        var minValueItem: String = ""
        var maxValueItem: String = ""
    }

    /**
     * The operating modes the Atmos controller accepts on a circuit.
     *
     * [expires] marks the two that end by themselves: the controller stores an
     * end time of day for AWAY and VISIT and falls back to AUTO when it passes.
     * Neither can be an automation target -- the job would find the circuit back
     * in AUTO on the next cycle and re-send the mode every five minutes for the
     * rest of the day.
     */
    enum class BoilerMode(val expires: Boolean) {
        AUTO(false),
        STANDBY(false),
        COMFORT(false),
        AWAY(true),
        VISIT(true)
    }

    /**
     * Follows the solid-fuel boiler: while it burns, take the heat; once it is
     * out, stop drawing from the tank and hand the heating back to its schedule.
     */
    class BoilerModeJobConfig {
        var statusItem: String = ""

        // Two signals, either of which means burning. The exhaust fan is the
        // direct "is there a fire" one and carries the whole burn -- but on an
        // overheat the boiler stops the fan while the water is at its hottest,
        // and reading that alone as "out" took both circuits off the boiler
        // exactly when the heat had nowhere else to go. Above
        // burningAboveCelsius the boiler has more than residual heat in it and
        // wants taking down whatever the fan is doing.
        //
        // It takes both of them to call the boiler out. One that cannot be read
        // is not a "no" -- the job holds and commands nothing.
        var runningItem: String = ""
        var temperatureItem: String = ""
        var burningAboveCelsius: Double = DEFAULT_BURNING_ABOVE_CELSIUS

        var heatingModeItem: String = ""
        var waterModeItem: String = ""

        // The four targets are item names, not modes: they are chosen from the
        // sitemap, so changing what "the boiler is burning" should mean is a tap
        // rather than a redeploy. Like every other setpoint here they live only
        // as openHAB item state -- see scripts/sync-item-states.sh.
        var runningHeatingModeItem: String = ""
        var runningWaterModeItem: String = ""
        var idleHeatingModeItem: String = ""
        var idleWaterModeItem: String = ""

        // Where the job records the mode it last left each circuit in. If a
        // circuit is somewhere else on the next cycle, somebody moved it by hand
        // and the job switches itself off rather than taking it back.
        var lastHeatingModeItem: String = ""
        var lastWaterModeItem: String = ""
    }

    /**
     * Whether the accumulation tank holds enough to get through tomorrow.
     *
     * The boiler is loaded by hand and fired in one go, so the decision it
     * supports is not "when should it burn" but "do I need to light it tomorrow".
     * Nothing here commands anything: it writes what it thinks into
     * [adviceItem], [neededItem], [shortfallItem] and [alertItem], and the alarm
     * table turns the last of those into a message. Wood is cheaper than
     * electricity, so being told in time is the whole saving.
     *
     * Demand is degree-days, which is the crudest useful model of a house and
     * needs no history: tomorrow's mean temperature against
     * [baseTemperatureCelsius], scaled by [chargePercentPerDegreeDay], less what
     * the sun is expected to bring. Those two are the numbers to correct after
     * watching it for a week; they are here rather than in item state because
     * they are calibration, not a daily choice.
     */
    class BoilerAdviceJobConfig {
        var statusItem: String = ""

        // What the tank holds now, as the 0-100 % the buffer rules already derive
        // from the four probes.
        var chargeItem: String = ""

        var minTomorrowItem: String = ""
        var maxTomorrowItem: String = ""

        // Tomorrow's radiation sum, in MJ/m2 -- the unit Open-Meteo answers in,
        // which is not the W/m2 of the hourly channels.
        var solarTomorrowItem: String = ""

        // When the forecast last arrived. A forecast that stopped coming looks
        // exactly like a current one, so anything deciding from it has to be able
        // to see its age.
        var forecastUpdatedItem: String = ""

        // Outputs. The advice is true all day; the alert is when it is worth
        // interrupting somebody, which is why they are two items.
        var adviceItem: String = ""
        var neededItem: String = ""
        var shortfallItem: String = ""
        var alertItem: String = ""

        var baseTemperatureCelsius: Double = DEFAULT_BASE_TEMPERATURE_CELSIUS

        // What a heating day costs before the weather is counted. Only charged on a day
        // that needs heating at all -- see the summer cliff in BoilerAdviceJob.
        var baselinePercentPerDay: Double = DEFAULT_BASELINE_PERCENT_PER_DAY

        var chargePercentPerDegreeDay: Double = DEFAULT_CHARGE_PERCENT_PER_DEGREE_DAY

        // Off by default: a solar credit that is guessed rather than measured makes
        // the advisory ask for too little wood, and a cold house is worse than a
        // tank that was fuller than it had to be.
        var chargePercentPerMegajoule: Double = 0.0

        var staleForecastHours: Long = DEFAULT_STALE_FORECAST_HOURS

        // The fire happens in the evening, so that is when the message is useful.
        var adviseFromHour: Int = DEFAULT_BOILER_ADVISE_FROM_HOUR
        var adviseToHour: Int = DEFAULT_BOILER_ADVISE_TO_HOUR
    }

    /**
     * How much of the house is below the band it is meant to be in, and whether
     * that is work for the boiler rather than for one room's own panel.
     *
     * One cold room is local work. Several at once are not: heating them all on
     * electricity costs more than a fire, and the boiler takes hours to bring the
     * system up, so it is worth asking for early rather than often. A cold room
     * with no [RoomConfig.localHeaterItem] escalates on its own, whatever the
     * count -- the kids' room has no panel, so the boiler is the only thing that
     * can answer for it.
     */
    class HouseDemandJobConfig {
        var statusItem: String = ""
        var rooms: List<RoomConfig> = emptyList()

        // Whether the boiler is already answering: heat still in the tank on its
        // way into the rooms, or a fire already lit. Neither being readable holds
        // the advice -- telling somebody to light a boiler that is already burning
        // is how an advisory stops being believed.
        var chargeItem: String = ""
        var burningItem: String = ""
        var tankCoversAbovePercent: Double = DEFAULT_TANK_COVERS_ABOVE_PERCENT

        var fireAboveColdRooms: Int = DEFAULT_FIRE_ABOVE_COLD_ROOMS

        // How far below its minimum a room has to sit before it counts as cold, so
        // that a room resting on its setpoint does not flicker in and out.
        var toleranceKelvin: Double = DEFAULT_TOLERANCE_KELVIN

        // The whole house is empty overnight, so it is said once here rather than copied
        // into every room. Nobody lights a boiler at three in the morning, and a house
        // left to coast until six is the point of the night setback, not a fault.
        var nightFromHour: Int = DEFAULT_NIGHT_FROM_HOUR
        var nightToHour: Int = DEFAULT_NIGHT_TO_HOUR

        var coldRoomsItem: String = ""
        var coldestRoomItem: String = ""
        var coldestGapItem: String = ""
        var alertItem: String = ""
    }

    /**
     * Which days a window applies to.
     *
     * Same vocabulary as [TimerJobConfig.Mode], which predates it and stays as it is;
     * the two could be unified, but that enum is reached from nine timer jobs in
     * production config and this is not the change to do it in.
     */
    enum class DayMode {
        ALL, WEEKDAY, WEEKEND;

        fun matches(date: java.time.LocalDate): Boolean {
            val weekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                date.dayOfWeek == java.time.DayOfWeek.SUNDAY
            return when (this) {
                ALL -> true
                WEEKDAY -> !weekend
                WEEKEND -> weekend
            }
        }
    }

    /**
     * One stretch of the day in which a room is empty, so its temperature does not
     * matter.
     *
     * Stated as exclusions rather than as occupancy because that is how the house is
     * actually known: the kid is at school on weekday mornings, the study is empty from
     * mid-afternoon, and everything is empty overnight. A room with no windows at all is
     * therefore always counted, which is the safe default -- it asks for heat rather
     * than silently going without.
     *
     * Crossing midnight is allowed and means what it reads like, so the night is 21 to 6.
     *
     * These are hours in configuration rather than item state, unlike the comfort bands.
     * A band is a preference somebody changes because they were chilly; a school
     * timetable changes twice a year, and "weekdays only" needs a day selector that a
     * Setpoint widget cannot express at all.
     */
    class OffWindow {
        var fromHour: Int = 0
        var toHour: Int = 0
        var mode: DayMode = DayMode.ALL
    }

    /**
     * One room's comfort band, and what can heat it locally.
     *
     * [minItem] and [maxItem] are openHAB item state, set from the sitemap, the
     * same shape MinMaxJob already uses for the brooder: below the minimum
     * something should heat, above the maximum it should stop, and in between
     * nothing needs saying. A room whose minimum has never been set reads NULL and
     * gets no vote, which is how the rooms that are only monitored -- the hallway,
     * the bathroom, the entry hall -- stay out of it without being special cases.
     */
    class RoomConfig {
        var name: String = ""
        var temperatureItem: String = ""
        var minItem: String = ""
        var maxItem: String = ""

        // Empty where the room has no electric heating of its own. Then being cold
        // is something only the boiler can fix.
        var localHeaterItem: String = ""

        // Empty where the room has no contact -- only two have one. ON is an open
        // window: they publish zigbee2mqtt's alarm_1 with on="true". A room airing
        // is cold for a reason and does not ask for a fire.
        var windowItem: String = ""

        // When this room in particular is empty, on top of the house-wide night. The
        // kid is at school on weekday mornings; the study is empty from mid-afternoon.
        // Heating an empty room is the plainest waste in the house.
        var offWindows: List<OffWindow> = emptyList()
    }

    /**
     * Whether one room's electric panel should be on -- as a suggestion, not a
     * command.
     *
     * The panels are backup. Electricity costs more than wood, so a panel is only
     * ever suggested for a room the boiler is not already heating: not while the
     * circuit pump runs, and not while the tank still holds heat above
     * [tankCoversAbovePercent]. Nor when the house as a whole is cold, because
     * then the answer is a fire and one message about the house beats one per
     * room. Beyond that it is MinMaxJob's own rule -- on below the minimum, off
     * above the maximum -- and it deliberately stops at saying so. [switchItem] is
     * only ever read.
     */
    class PanelAdviceJobConfig {
        var statusItem: String = ""

        // Which room in [HouseDemandJobConfig.rooms] this panel heats. The occupancy and
        // the band live there, in one place, so the panel advice cannot drift out of
        // step with the vote the same room casts for a fire.
        var roomName: String = ""

        // Read, never commanded. Its state is what the advice is compared against
        // to decide whether there is anything worth saying at all.
        var switchItem: String = ""

        var temperatureItem: String = ""

        // The same two items the room votes with in HouseDemandJobConfig, not a
        // second copy: one band per room, set in one place.
        var minItem: String = ""
        var maxItem: String = ""

        var windowItem: String = ""
        var circuitPumpItem: String = ""
        var chargeItem: String = ""
        var tankCoversAbovePercent: Double = DEFAULT_TANK_COVERS_ABOVE_PERCENT

        var adviceItem: String = ""
        var alertItem: String = ""

        // How long the room has to stay in use for switching a panel on to be worth it.
        // Electricity put into a room that empties in ten minutes buys nothing -- unlike
        // a late fire, which is not wasted because it charges the tank for tomorrow, so
        // no such rule applies to the boiler.
        var minimumRunMinutes: Int = DEFAULT_MINIMUM_RUN_MINUTES

        var adviseFromHour: Int = DEFAULT_PANEL_ADVISE_FROM_HOUR
        var adviseToHour: Int = DEFAULT_PANEL_ADVISE_TO_HOUR
    }
}
