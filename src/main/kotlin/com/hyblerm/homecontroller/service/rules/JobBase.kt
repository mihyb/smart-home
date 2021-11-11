package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

abstract class JobBase(val repository: DataAccess) {

    var items: MutableMap<String, OpenHabModel.Item> = mutableMapOf()

    fun item(name: String): OpenHabModel.Item {
        return items.computeIfAbsent(name) { repository.getItem(it) }
    }
}
