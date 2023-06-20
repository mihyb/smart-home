package com.hyblerm.homecontroller.service.rules.items

import com.hyblerm.homecontroller.service.repository.DataAccess

open class GenericItem(val id: String, val dataAccess: DataAccess) {

    fun command(value: String) {
        dataAccess.commandItem(id, value)
    }
}
