package com.hyblerm.homecontroller.service.rules.common.items

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

/**
 *
 *  Class representing a calendar item.
 *
 *  @property id The id of the switch.
 *  @property dataAccess The data access object used to communicate with the server.
 *  @property item The item associated with the switch.
 */
class CalendarItem(val id: String, val dataAccess: DataAccess) {

    var item: OpenHabModel.Item? = null

    /**
     *
     *  Checks if the item is on.
     *
     *  @return true if the item is on, false otherwise
     */
    fun isActive(): Boolean {
        if (item == null) {
            item = dataAccess.getItem(id)
        }
        val status = Gson().fromJson(item!!.state, CalendarStatus::class.java)
        return !status.calendar.inactive
    }

    override fun toString(): String {
        return "Switch(id='$id', item=$item)"
    }

    // @Serializable
    data class CalendarStatus(@SerializedName("100") val calendar: Calendar)
    data class Calendar(val inactive: Boolean)
}
