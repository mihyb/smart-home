package com.hyblerm.homecontroller.api

import com.hyblerm.homecontroller.service.rules.info.ElectricityWarningJob
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val CHECK_WEATHER_FOR_SOLAR_STATION = "checkWeatherForSolarStation"

@RestController
@RequestMapping("manual/job")
class ManualJobApi(
    val electricityWarningJob: ElectricityWarningJob
) {

    @PostMapping("{id}")
    fun startJob(@PathVariable id: String) {
        when (id) {
            CHECK_WEATHER_FOR_SOLAR_STATION -> electricityWarningJob.checkWeatherForSolarStation()
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$id is not supported")
        }
    }
}
