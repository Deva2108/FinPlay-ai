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
     *
     * Default spec: 30 s TTL, 500 entries max.
     * Per-endpoint overrides are registered via registerCustomCache() so hot
     * endpoints avoid the Redis network-hop without serving stale snapshots:
     *
     *   pulse    → 10 s  (user-specific, changes every scheduler tick)
     *   trending → 15 s  (batch-updated, safe to serve slightly stale)
     *   vibe     → 30 s  (AI-generated, stable within a market session)
     *   indices  → 20 s  (managed manually in MarketGateway, kept here for clarity)
     */
    @Bean
    public CacheManager l1CacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats());

        cacheManager.registerCustomCache("pulse",
                Caffeine.newBuilder().maximumSize(50)
                        .expireAfterWrite(10, TimeUnit.SECONDS).recordStats().build());
        cacheManager.registerCustomCache("trending",
                Caffeine.newBuilder().maximumSize(20)
                        .expireAfterWrite(15, TimeUnit.SECONDS).recordStats().build());
        cacheManager.registerCustomCache("vibe",
                Caffeine.newBuilder().maximumSize(10)
                        .expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build());
        cacheManager.registerCustomCache("indices",
                Caffeine.newBuilder().maximumSize(30)
                        .expireAfterWrite(20, TimeUnit.SECONDS).recordStats().build());

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
        //
        // Two-tier strategy with explicit key prefixes so Redis eviction (LRU under
        // memory pressure) can target AI noise before it touches core market data:
        //
        //   core::*   →  market data the UX cannot function without. Long TTL so
        //                a single Render/Redis hiccup doesn't blank the UI.
        //   ai::*     →  generated text. Shorter TTL, separate namespace. If memory
        //                fills, these are evicted first by name, not by chance.
        //
        // Note: the InsightAsyncService writes raw `insight:async:*` / `insight:status:*`
        // keys via RedisTemplate (not @Cacheable), so the topic whitelist in
        // InsightController is what keeps that namespace bounded.
        Map<String, RedisCacheConfiguration> configurations = new HashMap<>();

        // ── core:: market data — longer TTL, higher survival priority ──
        RedisCacheConfiguration coreConfig = standardConfig.prefixCacheNameWith("core::");
        configurations.put("hotQuotes",   coreConfig.entryTtl(Duration.ofMinutes(5)));
        configurations.put("marketNews",  coreConfig.entryTtl(Duration.ofHours(2)));   // was 1h
        configurations.put("stockCharts", coreConfig.entryTtl(Duration.ofHours(4)));   // was 1h — chart history rarely changes

        // ── ai:: generated text — capped TTL, easier to evict ──
        RedisCacheConfiguration aiConfig = standardConfig.prefixCacheNameWith("ai::");
        configurations.put("aiExplanations", aiConfig.entryTtl(Duration.ofMinutes(45))); // was 2h
        configurations.put("userInsights",   aiConfig.entryTtl(Duration.ofHours(1)));    // was 2h
        configurations.put("insights",       aiConfig.entryTtl(Duration.ofHours(1)));    // was 2h

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(standardConfig)
                .withInitialCacheConfigurations(configurations)
                .transactionAware()
                .build();
    }
}
