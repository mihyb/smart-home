package com.hyblerm.homecontroller.repository

import com.hyblerm.homecontroller.config.ConfigurationProperties
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import org.springframework.stereotype.Repository
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

@Repository
class OpenHabRepository(val properties: ConfigurationProperties) : DataAccess {

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

    override fun getItem(name: String): OpenHabModel.Item {
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
        WebClient.builder().build()
            .post()
            .uri(
                UriComponentsBuilder.fromHttpUrl(properties.openhab.baseUrl)
                    .path(properties.openhab.itemRelativePath)
                    .buildAndExpand(name).toUri()
            )
            .body(BodyInserters.fromValue(value))
            .retrieve()
            .toEntity(OpenHabModel.Item::class.java)
            .block()
    }
}
