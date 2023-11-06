package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.entity.ElectricityRate
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRateProvider
import com.hyblerm.homecontroller.service.repository.electricity.ElectricityRates
import com.hyblerm.homecontroller.service.util.Time
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class BuyElectricityDailyRateLoader(val repository: ElectricityRepository, val configuration: ConfigurationProperties, val time: Time) : ElectricityRateProvider {

    val logger: Logger = LoggerFactory.getLogger(BuyElectricityDailyRateLoader::class.java)

    override fun getHourlyRates(day: OffsetDateTime): ElectricityRates {
        return ElectricityRates(loadRatesFromCache(day) ?: loadRates(day))
    }

    private fun loadRatesFromCache(day: OffsetDateTime): Map<Int, Double>? {
        val rates = repository.findByHourTimeBetween(getStartHour(day), getEndHour(day))
        if (rates.isEmpty()) return null
        logger.debug("Loaded cached rates for $day")
        return rates.associate { it.hourTime.hour to it.rate }
    }

    private fun saveRates(day: OffsetDateTime, rates: Map<Int, Double>) {
        try {
            repository.saveAll(rates.map { ElectricityRate(hourTime = day.withHour(it.key), rate = it.value) })
        } catch (e: Exception) {
            logger.error("Unabled to save daily rates to database. Cache is not working.", e)
        }
    }

    private fun getEndHour(day: OffsetDateTime) = day.withHour(23).withMinute(59).withSecond(59)
    private fun getStartHour(day: OffsetDateTime) = day.minusDays(1).withHour(23).withMinute(59).withSecond(59)

    private fun loadRates(day: OffsetDateTime): Map<Int, Double> {
        val dayFormatted = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val document = Jsoup.connect(configuration.electricity.oteUrl + dayFormatted).get()
        val rates = document.select("div.bigtable > table.report_table > tbody > tr")
            .filter { r -> r.select("td").size > 3 }
            .filter { r -> r.select("th").isNotEmpty() }
            .associate {
                Pair(
                    it.select("th").first()?.text()?.toInt()?.minus(1) ?: 0, // OTE is shifted by one hour
                    it.select("td").first()?.text()?.replace(",", ".")?.toDouble() ?: 0.0
                )
            }
        saveRates(day, rates)
        return rates
    }

    override fun getBuyPriceCZK(hour: Int): Double? {
        return getHourlyRates(OffsetDateTime.now(time.clock()))
            .getRate(hour, configuration.currency.eurRate)
            ?.div(1000)
            ?.plus(configuration.electricity.buy.fixedPriceKwh)
    }
}
