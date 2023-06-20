package com.hyblerm.homecontroller.service.repository.electricity

import com.hyblerm.homecontroller.service.util.Time
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.time.ZoneId

@Service
class ElectricityPriceEvaluator(
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time
) {

    fun isElectricityFree(): Boolean {
        val currentHour: Int = LocalTime.ofInstant(time.clock().instant(), ZoneId.systemDefault()).hour
        val price = electricityRateProvider.getBuyPriceCZK(currentHour)
        return (price?.compareTo(0) ?: 0) <= 0
    }
}
