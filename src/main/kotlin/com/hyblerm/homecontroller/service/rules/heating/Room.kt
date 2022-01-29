package com.hyblerm.homecontroller.service.rules.heating

class Room(val name: String, val temperature: Double, private val deviance: Int, private val requestedTemp: Double) {

    fun getRequestedTemp(): Double {
        return requestedTemp + deviance
    }

    fun getRequestedAction(): Mode {
        val devReqTemp = getRequestedTemp()
        return if (temperature > devReqTemp) Mode.COOL else if (temperature < devReqTemp) Mode.HEAT else Mode.KEEP
    }

    enum class Mode {
        COOL,
        HEAT,
        KEEP
    }
}
