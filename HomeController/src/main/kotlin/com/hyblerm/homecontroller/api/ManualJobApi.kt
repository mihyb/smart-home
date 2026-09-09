package com.hyblerm.homecontroller.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("manual/job")
class ManualJobApi() {

    @PostMapping("{id}")
    fun startJob(@PathVariable id: String) {
        when (id) {
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$id is not supported")
        }
    }
}
