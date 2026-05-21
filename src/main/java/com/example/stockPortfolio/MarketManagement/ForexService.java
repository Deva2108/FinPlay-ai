package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Maintains a Redis-cached USD↔INR rate pulled from the open ExchangeRate-API
 * (https://open.er-api.com/v6/latest/USD).
 *
 * Used by Portfolio totals and the Insights "global → India" framing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ForexService {

    private static final String FOREX_KEY = "forex:USDINR";
    private static final long TTL_MINUTES = 10;
    private static final long TTL_HOURS = 1;

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${forex.base-url:https://open.er-api.com/v6/latest}")
    private String baseUrl;

    private static final BigDecimal FALLBACK_RATE = new BigDecimal("83.0");

    @org.springframework.cache.annotation.Cacheable(value = "forex", cacheManager = "l1CacheManager")
    public ForexQuoteDTO getCachedUsdInr() {
        try {
            Object data = redisTemplate.opsForValue().get(FOREX_KEY);
            if (data instanceof ForexQuoteDTO dto) return dto;
            if (data instanceof Map<?, ?> map) {
                Object rate = map.get("rate");
                if (rate != null) {
                    return ForexQuoteDTO.builder()
                            .base("USD")
                            .quote("INR")
                            .rate(new BigDecimal(rate.toString()))
                            .asOf(LocalDateTime.now())
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Forex cache read failed: {}", e.getMessage());
        }
        
        // Return hardcoded fallback if everything else fails to ensure system doesn't return null
        return ForexQuoteDTO.builder()
                .base("USD")
                .quote("INR")
                .rate(FALLBACK_RATE)
                .asOf(LocalDateTime.now())
                .build();
    }

    /** Convert a USD amount to INR using the best available rate. */
    public BigDecimal convertUsdToInr(BigDecimal usd) {
        if (usd == null) return BigDecimal.ZERO;
        ForexQuoteDTO rate = getCachedUsdInr();
        BigDecimal effectiveRate = (rate != null && rate.getRate() != null) ? rate.getRate() : FALLBACK_RATE;
        return convertUsdToInr(usd, effectiveRate);
    }

    /** 
     * Bulk-friendly conversion that uses a pre-fetched rate (Phase 2 optimization). 
     */
    public BigDecimal convertUsdToInr(BigDecimal usd, BigDecimal effectiveRate) {
        if (usd == null) return BigDecimal.ZERO;
        BigDecimal rate = (effectiveRate != null) ? effectiveRate : FALLBACK_RATE;
        return usd.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Convert INR back to USD. */
    public BigDecimal convertInrToUsd(BigDecimal inr) {
        if (inr == null) return null;
        ForexQuoteDTO rate = getCachedUsdInr();
        if (rate == null || rate.getRate() == null || rate.getRate().signum() == 0) return null;
        return inr.divide(rate.getRate(), 4, java.math.RoundingMode.HALF_UP);
    }

    /** Scheduler hook: pulls latest USD→INR. */
    public void refreshUsdInr() {
        try {
            Map<?, ?> response = restTemplate.getForObject(baseUrl + "/USD", Map.class);
            if (response == null) return;

            Map<?, ?> rates = (Map<?, ?>) response.get("rates");
            if (rates == null) return;

            Object inr = rates.get("INR");
            if (inr == null) return;

            ForexQuoteDTO dto = ForexQuoteDTO.builder()
                    .base("USD")
                    .quote("INR")
                    .rate(new BigDecimal(inr.toString()))
                    .asOf(LocalDateTime.now())
                    .build();

            redisTemplate.opsForValue().set(FOREX_KEY, dto, TTL_HOURS, TimeUnit.HOURS);
            log.info("Refreshed USD→INR rate: {}", dto.getRate());
        } catch (Exception ex) {
            log.warn("Forex refresh failed: {}", ex.getMessage());
        }
    }
}

