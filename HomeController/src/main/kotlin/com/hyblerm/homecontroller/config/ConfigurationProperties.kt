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
     * An enum rather than a string so a typo in application.yaml fails at
     * startup. Commanding a mode the controller does not know is rejected
     * silently, which would look exactly like the automation not running.
     * AWAY and VISIT are here for completeness; only AUTO, STANDBY and COMFORT
     * are meaningful without also setting an end time.
     */
    enum class BoilerMode {
        AUTO, STANDBY, COMFORT, AWAY, VISIT
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
        var runningHeatingMode: BoilerMode = BoilerMode.COMFORT
        var runningWaterMode: BoilerMode = BoilerMode.COMFORT
        var idleHeatingMode: BoilerMode = BoilerMode.AUTO
        var idleWaterMode: BoilerMode = BoilerMode.STANDBY
    }
}
