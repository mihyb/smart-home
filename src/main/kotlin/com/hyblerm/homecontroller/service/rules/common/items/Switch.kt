package com.hyblerm.homecontroller.service.rules.common.items

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

/**
 *
 *  Class representing a switch.
 *
 *  @property id The id of the switch.
 *  @property dataAccess The data access object used to communicate with the server.
 *  @property item The item associated with the switch.
 */
class Switch(private val id: String, val dataAccess: DataAccess) {

    var item: OpenHabModel.Item? = null

    fun turnOn() {
        dataAccess.commandItem(id, "ON")
    }

    fun turnOff() {
        dataAccess.commandItem(id, "OFF")
    }

    /**
     *
     *  Checks if the item is on.
     *
     *  @return true if the item is on, false otherwise
     */
    fun isOn(): Boolean {
        if (item == null) {
            item = dataAccess.getItem(id)
        }
        return item!!.isOn()
    }

    override fun toString(): String {
        return "Switch(id='$id', item=$item)"
    }
}
