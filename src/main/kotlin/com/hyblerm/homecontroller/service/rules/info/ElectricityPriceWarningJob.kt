package com.hyblerm.homecontroller.service.rules.info

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.rules.items.MessageItem
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class ElectricityPriceWarningJob(
    val dataAccess: DataAccess,
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time
) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    val messageItem = MessageItem(dataAccess)

    @Scheduled(cron = "0 55 * ? * *")
    fun checkNegativePrice() {

        val currentPrice = getPrice(time.clock())
        val nextPrice = getPrice(Clock.offset(time.clock(), Duration.ofHours(1)))

        logger.debug("Evaluating negative price warning. currentPrice: $currentPrice nextPrice: $nextPrice")

        if (currentPrice > 0 && nextPrice <= 0) {
            messageItem.command("Turn off electricity runoff. Price will be $nextPrice next hour")
        } else if (currentPrice <= 0 && nextPrice > 0) {
            messageItem.command("Turn on electricity runoff. Price will be $nextPrice next hour")
        }
    }

    private fun getPrice(clock: Clock): Double {
        val rates = electricityRateProvider.getHourlyRates(OffsetDateTime.now(clock))
        val hour: Int = LocalTime.ofInstant(clock.instant(), ZoneId.systemDefault()).hour
        return rates.getRate(hour) ?: 0.0
    }
}
