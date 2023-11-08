package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var nicehash = Nicehash()

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
    // app.nicehash.l3PlusIncomeUrl
    class Nicehash {
        var l3PlusIncomeUrl = ""
        var miningEnabled = false
    }
}
