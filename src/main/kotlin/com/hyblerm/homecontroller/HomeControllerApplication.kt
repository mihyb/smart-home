package com.hyblerm.homecontroller

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HomeControllerApplication

fun main(args: Array<String>) {
    runApplication<HomeControllerApplication>(*args)
}
