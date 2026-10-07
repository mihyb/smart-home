package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess

abstract class JobBase(val repository: DataAccess) {

    fun item(name: String): OpenHabModel.Item {
        return repository.getItem(name)
    }

    /**
     * Command an item only when it does not already hold the value.
     *
     * Every command lands on the event bus and in persistence, so a job that
     * recomputes the same answer every five minutes would fill both with a value
     * nothing has changed about -- and an item that never stops changing is one
     * whose real changes cannot be seen. Same rule BoilerModeJob follows for the
     * circuit modes, and there it also keeps the gateway's socket table intact.
     */
    fun publish(name: String, value: String) {
        if (item(name).state != value) {
            repository.commandItem(name, value)
        }
    }
}
