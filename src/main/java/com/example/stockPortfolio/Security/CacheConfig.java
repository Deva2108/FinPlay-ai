package com.example.stockPortfolio.Security;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * L1: IN-MEMORY CACHE (CAFFEINE)
     */
    @Bean
    public CacheManager l1CacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats());
        return cacheManager;
    }

    /**
     * L2: DISTRIBUTED CACHE (REDIS)
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper primaryObjectMapper) {
        // Reuse Spring's primary ObjectMapper instead of allocating a fresh one
        // (avoids a heavy ObjectMapper init during startup; JavaTimeModule is
        // already registered by Spring Boot's Jackson auto-config).
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(primaryObjectMapper);

        // Standard config: JSON values, String keys
        RedisCacheConfiguration standardConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Tiered configs
        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();
        
        configurations.put("hotQuotes", standardConfig.entryTtl(Duration.ofMinutes(5)));
        configurations.put("marketNews", standardConfig.entryTtl(Duration.ofHours(1)));
        configurations.put("stockCharts", standardConfig.entryTtl(Duration.ofHours(1)));
        configurations.put("aiExplanations", standardConfig.entryTtl(Duration.ofHours(2)));
        configurations.put("userInsights", standardConfig.entryTtl(Duration.ofHours(2)));
        configurations.put("insights", standardConfig.entryTtl(Duration.ofHours(2))); // Add the missing 'insights' cache

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(standardConfig)
                .withInitialCacheConfigurations(configurations)
                .transactionAware()
                .build();
    }
}
