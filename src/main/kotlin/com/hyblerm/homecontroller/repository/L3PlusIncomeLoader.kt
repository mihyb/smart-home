package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.service.repository.mining.L3IncomeProvider
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class L3PlusIncomeLoader(val configuration: ConfigurationProperties) : L3IncomeProvider {

    val pattern = "\\$(.*)".toRegex()

    val logger: Logger = LoggerFactory.getLogger(L3PlusIncomeLoader::class.java)

    override fun getDailyIncomeUsd(): Double {
        val document = Jsoup.connect(configuration.nicehash.l3PlusIncomeUrl).get()
        val priceText = document.select("table.rentability > tbody > tr")
            .filter { r -> r.select("td").any { td -> td.text().contains("Income") } }
            .flatMap { r -> r.select("td") }[1]
            .text()
        val groups = pattern.matchEntire(priceText)?.groups
        val usdPrice = groups?.get(1)
        if (usdPrice == null) {
            logger.error("Unable to load BTC income rate for l3+")
        }
        return usdPrice?.value?.toDouble() ?: 0.0
    }
}
