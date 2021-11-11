package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataNotifier
import org.springframework.stereotype.Repository

@Repository
class DataNotifierRepository : DataNotifier {

    override fun notifyItems(items: List<OpenHabModel.Item>) {
        // TODO ("Not yet implemented")
    }
}
