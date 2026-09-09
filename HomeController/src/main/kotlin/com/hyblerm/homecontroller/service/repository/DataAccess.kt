package com.hyblerm.homecontroller.service.repository

import com.hyblerm.homecontroller.repository.entity.OpenHabModel

interface DataAccess {
    fun getItems(): List<OpenHabModel.Item>
    fun getItem(name: String): OpenHabModel.Item
    fun commandItem(name: String, value: String)
}
