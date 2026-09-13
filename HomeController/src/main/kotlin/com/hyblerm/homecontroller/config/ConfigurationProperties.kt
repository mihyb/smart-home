package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var timerJobs = mutableListOf<TimerJobConfig>()
    var minMaxJobs = mutableListOf<MinMaxJobConfig>()
    var boilerModeJobs = mutableListOf<BoilerModeJobConfig>()

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
        var runningItem: String = ""
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
    }
}
