package com.hyblerm.homecontroller.service.rules.common.job

/**
 * The hours of the day in which an advisory may interrupt you.
 *
 * Separate from the advice itself on purpose: whether the tank will cover
 * tomorrow is true at four in the morning too, it is just not worth being woken
 * for. The alarm table's quiet hours cannot express this -- they say when *not*
 * to interrupt, and this says when the answer is actionable, which for a boiler
 * you load by hand is the evening.
 *
 * Crossing midnight is allowed, so 20 to 2 means what it reads like.
 */
object AdviceHours {

    fun contains(hour: Int, fromHour: Int, toHour: Int): Boolean {
        return if (fromHour <= toHour) {
            hour >= fromHour && hour < toHour
        } else {
            hour >= fromHour || hour < toHour
        }
    }
}
