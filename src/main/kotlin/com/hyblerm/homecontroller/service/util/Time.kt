package com.hyblerm.homecontroller.service.util

import java.time.Clock

open class Time {
    fun clock(): Clock {
        return Clock.systemDefaultZone()
    }
}
