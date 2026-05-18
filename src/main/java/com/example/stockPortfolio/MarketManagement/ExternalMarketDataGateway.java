package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.data.redis.core.RedisTemplate;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExternalMarketDataGateway {

    private final RestTemplate restTemplate;
    private final SymbolNormalizer symbolNormalizer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final YahooFinanceService yahooFinanceService;
    private final GoogleNewsRssService googleNewsRssService;

    private static final String LAST_UPDATED_TS_PREFIX = "stock:lastUpdated:";
    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final long FRESHNESS_THRESHOLD_MS = 10 * 60 * 1000L;

    // Symbol overrides for Indian markets (H6)
    private static final Map<String, String> SYMBOL_OVERRIDE_MAP = Map.of(
        "JIOFIN.NS", "JIOFIN:NSE",
        "ZOMATO.NS", "ZOMATO:NSE",
        "PAYTM.NS", "PAYTM:NSE",
        "NYKAA.NS", "FSNRENEW:NSE"
    );

    @Value("${finnhub.api.key}")
    private String finnhubApiKey;

    @Value("${finnhub.base-url}")
    private String finnhubBaseUrl;

    @Value("${news.api.key}")
    private String newsApiKey;

    @Value("${news.api.base-url}")
    private String newsApiBaseUrl;

    @Value("${twelvedata.api.key:}")
    private String twelveDataApiKey;

    @Value("${twelvedata.base-url:https://api.twelvedata.com}")
    private String twelveDataBaseUrl;

    @Value("${alpha.vantage.key:}")
    private String alphaVantageApiKey;

    @PostConstruct
    public void validateConfig() {
        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            log.warn("Finnhub API key is missing or mock. Using Yahoo Public as primary for US.");
        }
        if (newsApiKey == null || newsApiKey.isBlank() || "mock".equalsIgnoreCase(newsApiKey)) {
            log.warn("News API key is missing or mock. Using Google RSS as primary.");
        }
    }

    /**
     * Tries to fetch a quote from the best available source.
     * New Fallback Chain: Yahoo Public (Free) -> Twelve Data (Key) -> Finnhub (Key) -> AlphaVantage (Key) -> Mock
     * 
     * IMPORTANT: This method is UNTHROTTLED because it manages its own fallback hierarchy.
     */
    public Map<String, Object> fetchQuoteWithFallback(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return null;

        // 0. FRESHNESS GUARD: If data was updated within 10 minutes, return cached value
        Map<String, Object> cachedQuote = getCachedQuoteIfFresh(normalized);
        if (cachedQuote != null) {
            log.debug("Freshness guard: {} is fresh, skipping API call", normalized);
            return cachedQuote;
        }

        // 1. TRY YAHOO PUBLIC (Unlimited, Free, covers US & India)
        // This is the "SHIELD" - it has NO local rate limiter because it's public.
        try {
            Map<String, Object> yahooQuote = yahooFinanceService.fetchPublicQuote(normalized);
            if (yahooQuote != null) return yahooQuote;
        } catch (Exception e) {
            log.debug("Yahoo Public failed for {}, trying authenticated providers...", normalized);
        }

        // 2. TRY AUTHENTICATED PROVIDERS (Managed by local RateLimiters)
        // If these fail OR are locally rate-limited, we catch the exception and return mock.
        try {
            Map<String, Object> twelveQuote = fetchTwelveDataQuote(normalized);
            if (twelveQuote != null) return twelveQuote;
        } catch (Exception e) {
            log.debug("Twelve Data failed or throttled for {}", normalized);
        }

        try {
            if (symbolNormalizer.isIndian(normalized)) {
                Map<String, Object> ind = fetchIndianQuote(normalized);
                if (ind != null) return ind;
            } else {
                Map<String, Object> st = fetchStockQuote(normalized);
                if (st != null) return st;
            }
        } catch (Exception e) {
            log.error("All real providers failed for {}. Returning stable mock.", normalized);
        }

        return generateMockQuote(normalized);
    }

    /**
     * Generates a stable, hash-based mock price when all external APIs fail.
     * Crucial: No minute-based drift to prevent users from gaming the system during outages.
     */
    public Map<String, Object> generateMockQuote(String symbol) {
        Map<String, Object> res = new HashMap<>();
        res.put("symbol", symbol);
        
        // Base price is static per symbol to ensure consistency
        double basePrice = Math.abs(symbol.hashCode() % 500) + 50.0;
        
        res.put("price", Math.round(basePrice * 100.0) / 100.0);
        res.put("changesPercentage", 0.0);
        res.put("c", basePrice); // Legacy Finnhub compat
        res.put("dp", 0.0);
        res.put("source", "emergency_mock");
        return res;
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public Map<String, Object> fetchTwelveDataQuote(String symbol) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank() || "mock".equalsIgnoreCase(twelveDataApiKey)) return null;

        String normalized = symbolNormalizer.normalize(symbol);
        
        // 1. Resolve TwelveData Symbol
        String twelveSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
        if (twelveSymbol == null) {
            twelveSymbol = normalized;
            if (symbolNormalizer.isIndian(normalized)) {
                twelveSymbol = normalized.replace(".NS", ":NSE");
            }
        }

        // OPTIMIZATION: Use /quote endpoint directly (includes price and change in 1 credit)
        String quoteUrl = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/quote")
                .queryParam("symbol", twelveSymbol)
                .queryParam("apikey", twelveDataApiKey)
                .toUriString();

        try {
            Map<String, Object> quoteResponse = restTemplate.getForObject(quoteUrl, Map.class);
            if (quoteResponse == null || !quoteResponse.containsKey("price")) {
                return null;
            }

            double price = safeParseDouble(quoteResponse.get("price"));
            double changePct = safeParseDouble(quoteResponse.getOrDefault("percent_change", "0"));

            Map<String, Object> res = new HashMap<>();
            res.put("symbol", normalized);
            res.put("price", price);
            res.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
            res.put("source", "twelvedata");
            return res;
        } catch (Exception e) {
            log.warn("Twelve Data API error for {}: {}", twelveSymbol, e.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = "alphavantage", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "alphavantage")
    @Retry(name = "default")
    public Map<String, Object> fetchIndianQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || !symbolNormalizer.isIndian(normalized)) {
            return null;
        }

        if (alphaVantageApiKey == null || alphaVantageApiKey.isBlank() || "mock".equalsIgnoreCase(alphaVantageApiKey)) {
            return null;
        }

        // Check for specific overrides first
        String alphavSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
        if (alphavSymbol == null) {
            alphavSymbol = normalized.replace(".NS", ".BSE");
        }

        String url = UriComponentsBuilder.fromHttpUrl("https://www.alphavantage.co/query")
                .queryParam("function", "GLOBAL_QUOTE")
                .queryParam("symbol", alphavSymbol)
                .queryParam("apikey", alphaVantageApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !response.containsKey("Global Quote")) {
            return null;
        }

        Map<String, Object> quote = (Map<String, Object>) response.get("Global Quote");
        if (quote == null || quote.isEmpty()) {
            return null;
        }

        double current = safeParseDouble(quote.get("05. price"));
        double changePct = safeParseDouble(quote.get("10. change percent").toString().replace("%", ""));

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", normalized);
        result.put("price", current);
        result.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        result.put("source", "alphavantage");
        return result;
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "quoteFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public Map<String, Object> fetchStockQuote(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || symbolNormalizer.isIndian(normalized)) {
            return null;
        }

        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            return null;
        }

        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/quote")
                .queryParam("symbol", normalized)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || response.get("c") == null) {
            return null;
        }

        double current = safeParseDouble(response.get("c"));
        double previousClose = safeParseDouble(response.getOrDefault("pc", 0));
        double changePct = previousClose != 0 ? ((current - previousClose) / previousClose) * 100 : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", normalized);
        result.put("price", current);
        result.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        result.put("source", "finnhub");
        return result;
    }

    @CircuitBreaker(name = "newsapi", fallbackMethod = "listFallback")
    @RateLimiter(name = "newsapi")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchMarketNews(String query) {
        // 1. ALWAYS TRY GOOGLE RSS FIRST (Free, Unlimited)
        try {
            List<Map<String, Object>> rssNews = googleNewsRssService.fetchRssNews(query);
            if (!rssNews.isEmpty()) return rssNews;
        } catch (Exception e) {
            log.warn("Google RSS failed, trying authenticated sources...");
        }

        // 2. FALLBACK TO NEWSAPI (Authenticated)
        if (newsApiKey == null || newsApiKey.isBlank() || "mock".equalsIgnoreCase(newsApiKey)) {
            return Collections.emptyList();
        }

        String normalized = query == null || query.isBlank() ? "stock market" : query;
        String url = UriComponentsBuilder.fromHttpUrl(newsApiBaseUrl + "/everything")
                .queryParam("q", normalized)
                .queryParam("sortBy", "publishedAt")
                .queryParam("language", "en")
                .queryParam("pageSize", 10)
                .queryParam("apiKey", newsApiKey)
                .toUriString();

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("articles")) return Collections.emptyList();

            List<Map<String, Object>> articles = (List<Map<String, Object>>) response.get("articles");
            return articles.stream().limit(10).map(article -> {
                Map<String, Object> item = new HashMap<>();
                item.put("headline", article.get("title"));
                Map<String, Object> source = (Map<String, Object>) article.get("source");
                item.put("source", source != null ? source.get("name") : "NewsAPI");
                item.put("datetime", article.get("publishedAt"));
                item.put("url", article.get("url"));
                return item;
            }).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "listFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchCompanyNews(String symbol) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null || symbolNormalizer.isIndian(normalized)) {
            return Collections.emptyList();
        }

        if (finnhubApiKey == null || finnhubApiKey.isBlank() || "mock".equalsIgnoreCase(finnhubApiKey)) {
            return Collections.emptyList();
        }

        String today = LocalDate.now().toString();
        String lastWeek = LocalDate.now().minusDays(7).toString();
        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/company-news")
                .queryParam("symbol", normalized)
                .queryParam("from", lastWeek)
                .queryParam("to", today)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        try {
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            if (response == null) return Collections.emptyList();

            return response.stream().limit(10).map(item -> {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("headline", item.get("headline"));
                simplified.put("source", item.get("source"));
                Object datetime = item.get("datetime");
                if (datetime instanceof Number number) {
                    simplified.put("datetime", Instant.ofEpochSecond(number.longValue()).toString());
                } else if (datetime != null) {
                    simplified.put("datetime", datetime.toString());
                }
                simplified.put("url", item.get("url"));
                return simplified;
            }).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Fallbacks
    public Map<String, Object> quoteFallback(String symbol, Exception e) {
        log.warn("Fallback triggered for quote {}: {}", symbol, e.getMessage());
        return null;
    }

    public List<Map<String, Object>> listFallback(String symbol, Exception e) {
        log.warn("Fallback triggered for news/list {}: {}", symbol, e.getMessage());
        return Collections.emptyList();
    }

    public List<Map<String, Object>> fetchHistoricalData(String symbol) {
        return fetchHistoricalData(symbol, "1Y");
    }

    public List<Map<String, Object>> fetchHistoricalData(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        if (normalized == null) return Collections.emptyList();

        // 1. TRY TWELVE DATA (Global)
        try {
            List<Map<String, Object>> twelveCharts = fetchTwelveDataCandles(normalized, range);
            if (twelveCharts != null && !twelveCharts.isEmpty()) return twelveCharts;
        } catch (Exception e) {
            log.warn("Twelve Data charts failed for {}", normalized);
        }

        // 2. TRY SECONDARY (Finnhub for US, AlphaVantage for India)
        return fetchStockCandles(normalized);
    }

    public List<Map<String, Object>> fetchTwelveDataCandles(String symbol) {
        return fetchTwelveDataCandles(symbol, "1Y");
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "listFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchTwelveDataCandles(String symbol, String range) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank()) return Collections.emptyList();

        String normalized = symbolNormalizer.normalize(symbol);
        
        // Optimize outputsize based on range
        int outputSize = switch (range.toUpperCase()) {
            case "1M" -> 30;
            case "6M" -> 180;
            case "1Y" -> 365;
            case "3Y" -> 1095;
            default -> 365;
        };

        String twelveSymbol = normalized;
        if (symbolNormalizer.isIndian(normalized)) {
            twelveSymbol = normalized.replace(".NS", ":NSE");
        }

        String url = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/time_series")
                .queryParam("symbol", twelveSymbol)
                .queryParam("interval", "1day")
                .queryParam("outputsize", outputSize)
                .queryParam("apikey", twelveDataApiKey)
                .toUriString();

        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("values")) {
                return Collections.emptyList();
            }

            List<Map<String, String>> values = (List<Map<String, String>>) response.get("values");
            List<Map<String, Object>> formatted = new ArrayList<>();
            
            for (Map<String, String> val : values) {
                Map<String, Object> point = new HashMap<>();
                point.put("date", val.get("datetime")); // Standardized field name
                point.put("close", safeParseDouble(val.get("close")));
                formatted.add(point);
            }
            
            Collections.reverse(formatted);
            return formatted;
        } catch (Exception e) {
            log.error("Twelve Data candles failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches quotes for multiple symbols in a single API call (Batch).
     * Twelve Data supports up to 25-50 symbols per call. We chunk them for safety.
     */
    public Map<String, Map<String, Object>> fetchTwelveDataBatch(List<String> symbols) {
        if (twelveDataApiKey == null || twelveDataApiKey.isBlank() || symbols == null || symbols.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Resolve and Normalize symbols
        Map<String, String> reverseMap = new HashMap<>(); // TwelveSymbol -> OurNormalizedSymbol
        List<String> resolvedSymbols = new ArrayList<>();

        for (String s : symbols) {
            String normalized = symbolNormalizer.normalize(s);
            if (normalized == null) continue;

            String twelveSymbol = SYMBOL_OVERRIDE_MAP.get(normalized);
            if (twelveSymbol == null) {
                twelveSymbol = symbolNormalizer.isIndian(normalized) ? normalized.replace(".NS", ":NSE") : normalized;
            }
            resolvedSymbols.add(twelveSymbol);
            reverseMap.put(twelveSymbol, normalized);
        }

        // 2. Fetch in chunks of 25 (Safe limit for Twelve Data Free Tier)
        Map<String, Map<String, Object>> allResults = new HashMap<>();
        int requestCount = 0;
        for (int i = 0; i < resolvedSymbols.size(); i += 25) {
            if (requestCount >= 5) { // Safety limit: don't burst too many calls in one scheduler cycle
                log.warn("Twelve Data Batch quota safeguard: limiting to 5 chunks per cycle");
                break;
            }
            
            List<String> chunk = resolvedSymbols.subList(i, Math.min(i + 25, resolvedSymbols.size()));
            String batchString = String.join(",", chunk);

            String url = UriComponentsBuilder.fromHttpUrl(twelveDataBaseUrl + "/quote")
                    .queryParam("symbol", batchString)
                    .queryParam("apikey", twelveDataApiKey)
                    .toUriString();

            try {
                // Rate control delay to prevent instant 429
                if (requestCount > 0) Thread.sleep(1000); 
                
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                requestCount++;
                
                if (response == null) continue;

                // Twelve Data returns a single Map if only 1 symbol, or Map<Symbol, Map> if multiple
                if (chunk.size() == 1) {
                    processSingleTwelveQuote(response, reverseMap, allResults);
                } else {
                    for (String tSym : chunk) {
                        Object symData = response.get(tSym);
                        if (symData instanceof Map) {
                            processSingleTwelveQuote((Map<String, Object>) symData, reverseMap, allResults);
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Twelve Data Batch failed for chunk {}: {}", batchString, e.getMessage());
            }
        }
        return allResults;
    }

    private void processSingleTwelveQuote(Map<String, Object> data, Map<String, String> reverseMap, Map<String, Map<String, Object>> results) {
        if (data == null || !data.containsKey("symbol") || !data.containsKey("price")) return;
        
        String tSym = data.get("symbol").toString();
        String normalized = reverseMap.get(tSym);
        if (normalized == null) return;

        double price = safeParseDouble(data.get("price"));
        double changePct = safeParseDouble(data.getOrDefault("percent_change", "0"));

        Map<String, Object> quote = new HashMap<>();
        quote.put("symbol", normalized);
        quote.put("price", price);
        quote.put("changesPercentage", Math.round(changePct * 100.0) / 100.0);
        quote.put("source", "twelvedata_batch");
        results.put(normalized, quote);
    }

    @CircuitBreaker(name = "finnhub", fallbackMethod = "listFallback")
    @RateLimiter(name = "finnhub")
    @Retry(name = "default")
    public List<Map<String, Object>> searchStocks(String query) {
        String normalized = query == null ? "" : query;
        String url = UriComponentsBuilder.fromHttpUrl(finnhubBaseUrl + "/search")
                .queryParam("q", normalized)
                .queryParam("token", finnhubApiKey)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !response.containsKey("result")) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> searchResults = (List<Map<String, Object>>) response.get("result");
        if (searchResults == null || searchResults.isEmpty()) {
            return Collections.emptyList();
        }

        return searchResults.stream()
                .filter(r -> "Common Stock".equals(r.get("type")))
                .limit(5)
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("symbol", r.get("symbol"));
                    map.put("name", r.get("description"));
                    return map;
                })
                .toList();
    }

    @CircuitBreaker(name = "twelvedata", fallbackMethod = "listFallback")
    @RateLimiter(name = "twelvedata")
    @Retry(name = "default")
    public List<Map<String, Object>> fetchStockCandles(String symbol) {
        // MOCK MODE check
        if ("mock".equalsIgnoreCase(finnhubApiKey) || "mock".equalsIgnoreCase(twelveDataApiKey)) {
            return generateMockCandles(symbol);
        }
        // Finnhub candle is now premium only. Twelve Data is our primary for charts.
        return fetchTwelveDataCandles(symbol);
    }

    private List<Map<String, Object>> generateMockCandles(String symbol) {
        List<Map<String, Object>> list = new ArrayList<>();
        double base = Math.abs(symbol.hashCode() % 500) + 50.0;
        for (int i = 30; i >= 0; i--) {
            Map<String, Object> point = new HashMap<>();
            point.put("timestamp", LocalDate.now().minusDays(i).toString());
            double price = base + (Math.sin(i * 0.5) * 10.0);
            point.put("price", Math.round(price * 100.0) / 100.0);
            point.put("value", Math.round(price * 100.0) / 100.0);
            list.add(point);
        }
        return list;
    }

    public List<Map<String, Object>> fetchIndianStockCandles(String symbol) {
        // Redirecting to Twelve Data for consistency and quota protection
        return fetchTwelveDataCandles(symbol);
    }

    private boolean isFresh(String symbol) {
        try {
            Object ts = redisTemplate.opsForValue().get(LAST_UPDATED_TS_PREFIX + symbol);
            if (ts == null) return false;
            long lastUpdatedMs = Long.parseLong(ts.toString());
            return (System.currentTimeMillis() - lastUpdatedMs) < FRESHNESS_THRESHOLD_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> getCachedQuoteIfFresh(String symbol) {
        if (!isFresh(symbol)) return null;
        try {
            Object data = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + symbol);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
        } catch (Exception e) {
            log.debug("Cache read failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private double safeParseDouble(Object v) {
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
