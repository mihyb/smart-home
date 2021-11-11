package com.hyblerm.homecontroller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app")
class ConfigurationProperties {

    var openhab: OpenHab = OpenHab()

    class OpenHab {
        var baseUrl: String = ""
        var itemsRelativePath: String = "items"
        var itemRelativePath: String = "items/{name}"
    }
}
