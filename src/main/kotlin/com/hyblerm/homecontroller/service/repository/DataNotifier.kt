package com.hyblerm.homecontroller.service.repository

import com.hyblerm.homecontroller.repository.entity.OpenHabModel

interface DataNotifier {
    fun notifyItems(items:List<OpenHabModel.Item>)
}