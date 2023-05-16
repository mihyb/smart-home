package com.hyblerm.homecontroller.service.repository.electricity

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.util.Time
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Service
class ElectricityPriceEvaluator(
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time,
    val configuration: ConfigurationProperties
) {

    fun isElectricityFree(): Boolean {
        val currentHour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
        val hourlyRates = electricityRateProvider.getHourlyRates(OffsetDateTime.now(time.clock()))
        return (hourlyRates.getRate(currentHour, configuration.currency.eurRate)?.compareTo(0) ?: 0) <= 0
    }
}
