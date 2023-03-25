package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab = OpenHab()
    var electricity = Electricity()
    var nicehash = Nicehash()
    var currency = Currency()

    class Currency {
        var usdRate = 0.0
        var eurRate = 0.0
    }

    class Electricity {
        var oteUrl = ""
    }

    class OpenHab {
        var baseUrl: String = ""
        var itemsRelativePath: String = "items"
        var itemRelativePath: String = "items/{name}"
    }
    // app.nicehash.l3PlusIncomeUrl
    class Nicehash {
        var l3PlusIncomeUrl = ""
    }
}
