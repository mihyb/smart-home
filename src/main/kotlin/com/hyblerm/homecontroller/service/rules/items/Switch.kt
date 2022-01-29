package com.hyblerm.homecontroller.service.rules.items

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

class Switch(private val id: String, val dataAccess: DataAccess) {

    var item: OpenHabModel.Item? = null

    fun turnOn() {
        dataAccess.commandItem(id, "ON")
    }

    fun turnOff() {
        dataAccess.commandItem(id, "OFF")
    }

    fun isOn(): Boolean {
        if (item == null) {
            item = dataAccess.getItem(id)
        }
        return item!!.isOn()
    }
}
