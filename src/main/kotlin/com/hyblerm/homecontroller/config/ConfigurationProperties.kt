package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var timerJobs = mutableListOf<TimerJobConfig>()

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
        var switchItem: String = ""
        var statusItem: String = ""
        var startHourItem: String = ""
        var endHourItem: String = ""
    }
}
