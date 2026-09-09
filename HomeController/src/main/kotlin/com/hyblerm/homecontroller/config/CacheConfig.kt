package com.hyblerm.homecontroller.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.OffsetDateTime

@EnableCaching
@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val simpleCacheManager = SimpleCacheManager()
        simpleCacheManager.setCaches(
            listOf(
                CacheWithExpiration("item", 20)
            )
        )
        return simpleCacheManager
    }

    class CacheWithExpiration(name: String, private val expiration: Long) : ConcurrentMapCache(name) {

        override fun fromStoreValue(storeValue: Any?): Any {
            if (storeValue is TimedWrapper) {
                super.fromStoreValue(storeValue.value)
            }
            return super.fromStoreValue(storeValue)
        }

        override fun toStoreValue(userValue: Any?): Any {
            return super.toStoreValue(TimedWrapper(userValue, OffsetDateTime.now()))
        }

        override fun lookup(key: Any): Any? {
            val lookup = super.lookup(key)
            if (lookup is TimedWrapper) {
                return if (lookup.created.plusSeconds(expiration).isBefore(OffsetDateTime.now())) null else lookup.value
            }
            return lookup
        }
    }

    data class TimedWrapper(val value: Any?, val created: OffsetDateTime)
}
