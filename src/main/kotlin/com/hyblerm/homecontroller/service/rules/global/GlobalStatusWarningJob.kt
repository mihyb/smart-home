package com.hyblerm.homecontroller.service.rules.global

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.JobBase
import com.hyblerm.homecontroller.service.rules.items.MessageItem
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class GlobalStatusWarningJob(repository: DataAccess) : JobBase(repository) {

    val messageItem = MessageItem(repository)
    val statusItem = "lastUpdateStatus"

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun checkGlobalStatus() {
        val state = item(statusItem).state
        if (state == "DOWN") {
            messageItem.command("Some sensors are down")
        }
    }
}
