package com.hyblerm.homecontroller.repository.entity

import java.time.OffsetDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class ElectricityRate(
    @Id
    @GeneratedValue
    var id: Long = 0,
    var hourTime: OffsetDateTime,
    var rate: Double
)
