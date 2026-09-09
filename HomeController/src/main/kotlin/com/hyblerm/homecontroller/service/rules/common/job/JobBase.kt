package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

abstract class JobBase(val repository: DataAccess) {

    fun item(name: String): OpenHabModel.Item {
        return repository.getItem(name)
    }
}
