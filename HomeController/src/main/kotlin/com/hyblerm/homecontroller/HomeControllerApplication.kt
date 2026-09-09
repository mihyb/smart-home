package com.hyblerm.homecontroller

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAdminServer
class HomeControllerApplication

fun main(args: Array<String>) {
    runApplication<HomeControllerApplication>(*args)
}
