package com.hyblerm.homecontroller.service.rules.heating

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Room(val name: String, val temperature: Double, private val deviance: Int, private val requestedTemp: Double) {

    private val logger: Logger = LoggerFactory.getLogger(Room::class.java)

    fun getRequestedTemp(): Double {
        return requestedTemp + deviance
    }

    fun getRequestedAction(): Mode {
        val devReqTemp = getRequestedTemp()
        val requestedAction = if (temperature > devReqTemp) Mode.COOL else if (temperature < devReqTemp) Mode.HEAT else Mode.KEEP
        logger.debug("$name: Requested temp: $devReqTemp actual temp: $temperature -> requestedAction: $requestedAction")
        return requestedAction
    }

    enum class Mode {
        COOL,
        HEAT,
        KEEP
    }
}
