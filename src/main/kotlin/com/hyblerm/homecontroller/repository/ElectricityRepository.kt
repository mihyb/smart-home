package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.repository.entity.ElectricityRate
import org.springframework.data.repository.CrudRepository
import java.time.OffsetDateTime

interface ElectricityRepository : CrudRepository<ElectricityRate, Long> {

    fun findByHourTimeBetween(from: OffsetDateTime, to: OffsetDateTime): List<ElectricityRate>
}
