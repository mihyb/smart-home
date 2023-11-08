package com.hyblerm.homecontroller.service.repository.electricity

import java.time.OffsetDateTime

interface ElectricityRateProvider {

    fun getHourlyRates(day: OffsetDateTime, exchangeRate: Double = 1.0): ElectricityRates

    fun getBuyPriceCZK(hour: Int): Double?
}
