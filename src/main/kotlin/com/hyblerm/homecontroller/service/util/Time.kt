package com.hyblerm.homecontroller.service.util

import org.springframework.stereotype.Component
import java.time.Clock

@Component
open class Time {
    fun clock(): Clock {
        return Clock.systemDefaultZone()
    }
}
