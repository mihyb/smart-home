package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

@Repository
class OpenHabRepository(val properties: ConfigurationProperties) : DataAccess {

    val logger: Logger = LoggerFactory.getLogger(OpenHabRepository::class.java)

    override fun getItems(): List<OpenHabModel.Item> {
        val response = WebClient.builder().build()
            .get()
            .uri(
                UriComponentsBuilder.fromHttpUrl(properties.openhab.baseUrl)
                    .path(properties.openhab.itemsRelativePath)
                    .build().toUri()
            )
            .retrieve()
            .toEntityList(OpenHabModel.Item::class.java)
            .block()
        return response!!.body!!
    }

    @Cacheable("item")
    override fun getItem(name: String): OpenHabModel.Item {
        logger.trace("Getting state of item $name")
        val response = WebClient.builder().build()
            .get()
            .uri(
                UriComponentsBuilder.fromHttpUrl(properties.openhab.baseUrl)
                    .path(properties.openhab.itemRelativePath)
                    .buildAndExpand(name).toUri()
            )
            .retrieve()
            .toEntity(OpenHabModel.Item::class.java)
            .block()
        return response!!.body!!
    }

    override fun commandItem(name: String, value: String) {
        logger.debug("Item {} commanded to {}", name, value)
        WebClient.builder().build()
            .post()
            .uri(
                UriComponentsBuilder.fromHttpUrl(properties.openhab.baseUrl)
                    .path(properties.openhab.itemRelativePath)
                    .buildAndExpand(name).toUri()
            )
            .body(BodyInserters.fromValue(value))
            .retrieve()
            .onStatus(
                { status -> !status.is2xxSuccessful },
                { response -> Mono.error(RuntimeException("Failed to command item $name with response ${response.statusCode()}")) }
            )
            .toBodilessEntity()
            .block()
    }
}
