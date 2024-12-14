package com.hyblerm.homecontroller.service.repository.electricity

import com.hyblerm.homecontroller.service.util.Time
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Service
@Primary
class NegativeElectricityPriceEvaluator(
    val electricityRateProvider: ElectricityRateProvider,
    val time: Time
) : IElectricityPriceEvaluator {

    override fun isElectricityFree(): Boolean {
        return false
    }
}
