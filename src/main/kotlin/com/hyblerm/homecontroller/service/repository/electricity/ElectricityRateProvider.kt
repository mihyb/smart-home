package com.hyblerm.homecontroller.service.repository.electricity

import java.time.OffsetDateTime

interface ElectricityRateProvider {

    fun getHourlyRates(day: OffsetDateTime): ElectricityRates
}
