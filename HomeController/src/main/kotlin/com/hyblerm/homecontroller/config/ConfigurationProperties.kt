package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Hotter than residual heat in a boiler that has gone out, whatever the fan says. */
private const val DEFAULT_BURNING_ABOVE_CELSIUS = 85.0

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var timerJobs = mutableListOf<TimerJobConfig>()
    var minMaxJobs = mutableListOf<MinMaxJobConfig>()
    var boilerModeJobs = mutableListOf<BoilerModeJobConfig>()
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
}
