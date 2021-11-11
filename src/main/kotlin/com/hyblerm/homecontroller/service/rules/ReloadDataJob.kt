package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.DataNotifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ReloadDataJob(val repository: DataAccess, val dataNotifier: DataNotifier) {

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    fun printItems() {
        val items = repository.getItems()
        dataNotifier.notifyItems(items)
    }
}
