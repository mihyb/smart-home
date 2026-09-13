package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.MinMaxJobConfig
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class MinMaxJob(
    repository: DataAccess,
    private val config: MinMaxJobConfig
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger("${MinMaxJob::class.java.name}#${config.switchItem}")

    fun checkSwitch() {
        if (!item(config.statusItem).isOn()) {
            logger.debug("job is disabled")
            return
        }
        // The sensor reads NULL whenever its thing is offline, and the setpoints
        // read NULL until persistence restores them after a restart. Acting on a
        // missing temperature would mean guessing whether to heat, so skip the
        // cycle instead: the next one runs in five minutes.
        val currentValue = item(config.valueItem).getDoubleOrNull()
        val minValue = item(config.minValueItem).getDoubleOrNull()
        val maxValue = item(config.maxValueItem).getDoubleOrNull()
        if (currentValue == null || minValue == null || maxValue == null) {
            logger.warn(
                "skipping: no value for one of {} / {} / {}",
                config.valueItem, config.minValueItem, config.maxValueItem
            )
            return
        }
        val switch = Switch(config.switchItem, repository)
        when {
            currentValue < minValue -> {
                if (!switch.isOn()) {
                    logger.info("value {} below min {}, switching on", currentValue, minValue)
                    switch.turnOn()
                }
            }
            currentValue > maxValue -> {
                if (switch.isOn()) {
                    logger.info("value {} above max {}, switching off", currentValue, maxValue)
                    switch.turnOff()
                }
            }
            else -> logger.debug("value {} within [{}, {}], keeping state", currentValue, minValue, maxValue)
        }
    }
}
