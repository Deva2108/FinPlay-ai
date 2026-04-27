package com.example.stockPortfolio.Security;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 30-minute TTL for production efficiency
        RedisCacheConfiguration standardConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(standardConfig)
                .withCacheConfiguration("stockPrices", standardConfig)
                .withCacheConfiguration("stockQuotes", standardConfig)
                .withCacheConfiguration("marketNews", standardConfig)
                .withCacheConfiguration("marketVibe", standardConfig)
                .build();
    }
}
