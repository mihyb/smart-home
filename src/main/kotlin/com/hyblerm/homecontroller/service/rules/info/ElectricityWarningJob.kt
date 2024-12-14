package com.hyblerm.homecontroller.service.rules.info

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.rules.items.MessageItem
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val CLOUDINESS_TOMORROW_ITEM = "LocalOnecall_CloudinessTommorow"
private const val EXCHANGE_RATE_ITEM = "eurCzkExRate"

@Service
class ElectricityWarningJob(
    val dataAccess: DataAccess,
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time
) {

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    val messageItem = MessageItem(dataAccess)

    // @Scheduled(cron = "0 55 * ? * *")
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

    // @Scheduled(cron = "0 05 18 ? * *")
    fun checkWeatherForSolarStation() {

        val cloudinessItem = dataAccess.getItem(CLOUDINESS_TOMORROW_ITEM)
        val exchangeRate = dataAccess.getItem(EXCHANGE_RATE_ITEM).getDouble()

        val hourlyRates = electricityRateProvider.getHourlyRates(OffsetDateTime.now().plusDays(1), exchangeRate)
        val cheapMorningHours = hourlyRates.getCheapestRates(3, 0, 7)

        if (cloudinessItem.getPercent() > 80) {

            val cheapAfternoonHours = hourlyRates.getCheapestRates(2, 12, 20)
            val averagePriceAfternoon = hourlyRates.getAverageRate(12, 20)
            val percentDifferenceAfternoon = 100 - (cheapMorningHours.toList()[0].second.div(averagePriceAfternoon.div(100)))
            messageItem.command(
                "Turn on grid charging. " +
                    "It will be ${cloudinessItem.state} cloudy tomorrow. " +
                    "Morning cheap hours are: $cheapMorningHours" +
                    " and afternoon: $cheapAfternoonHours with difference: ${percentDifferenceAfternoon.absoluteValue.roundToInt()}"
            )
            logger.debug(
                "El charging eval params. cheapAfternoonHours: {}, averagePriceAfternoon: {} percentDifferenceAfternoon:{}",
                cheapAfternoonHours,
                averagePriceAfternoon,
                percentDifferenceAfternoon
            )
        } else {
            messageItem.command(
                "Turn on grid charging. " +
                    "It will be ${cloudinessItem.state} cloudy tomorrow. " +
                    "Morning cheap hours are: $cheapMorningHours"
            )
        }
    }
}
