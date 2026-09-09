package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.config.ConfigurationProperties
import lombok.extern.slf4j.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

@Primary
@Profile("test-mode")
@Slf4j
@Repository
class OpenHabReadOnlyRepository(override val properties: ConfigurationProperties) : OpenHabRepository(properties) {

    override val logger: Logger = LoggerFactory.getLogger(OpenHabReadOnlyRepository::class.java)

    override fun commandItem(name: String, value: String) {
        logger.info("Item {} commanded to {} - read only", name, value)
    }
}
