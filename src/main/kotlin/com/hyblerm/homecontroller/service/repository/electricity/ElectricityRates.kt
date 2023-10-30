package com.hyblerm.homecontroller.service.repository.electricity

class ElectricityRates(private val rates: Map<Int, Double>) {

    fun getCheapestRates(segmentsAmount: Int): Map<Int, Double> {
        return rates.toList().sortedBy { (_, value) -> value }.take(segmentsAmount).toMap()
    }

    fun getRate(hour: Int, exchangeRate: Double = 1.0): Double? {
        return rates[hour]?.times(exchangeRate)
    }

    fun getCheapestRates(segmentsAmount: Int, from: Int, to: Int): Map<Int, Double> {
        return rates.toList().filter { (key, _) -> key in from..to }.sortedBy { (_, value) -> value }.take(segmentsAmount).toMap()
    }

    fun getMostExpensiveRates(segmentsAmount: Int, from: Int, to: Int): Map<Int, Double> {
        return rates.toList().filter { (key, _) -> key in from..to }.sortedByDescending { (_, value) -> value }.take(segmentsAmount).toMap()
    }

    fun getAverageRate(from: Int, to: Int): Double {
        val values = rates.toList().filter { (key, _) -> key in from..to }
        return values.sumOf { entry -> entry.second } / values.size
    }
}
