package com.example.stockPortfolio.MarketManagement;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@Tag(name = "6. Market", description = "Real-time Market Data via Hybrid API System")
public class MarketController {

    @Autowired
    private FinnhubService finnhubService;

    @Autowired
    private YahooFinanceService yahooService;

    @Autowired
    private NewsApiService newsService;

    @Autowired
    private MarketAnalysisService marketAnalysisService;

    @GetMapping("/gainers")
    public ResponseEntity<List<Map<String, Object>>> getGainers(@RequestParam(required = false) String cap, @RequestParam(required = false) String sector) {
        return ResponseEntity.ok(marketAnalysisService.getGainers(cap, sector));
    }

    @GetMapping("/losers")
    public ResponseEntity<List<Map<String, Object>>> getLosers(@RequestParam(required = false) String cap, @RequestParam(required = false) String sector) {
        return ResponseEntity.ok(marketAnalysisService.getLosers(cap, sector));
    }

    @GetMapping("/sector")
    public ResponseEntity<List<Map<String, Object>>> getBySector(@RequestParam(required = false) String name) {
        return ResponseEntity.ok(marketAnalysisService.getBySector(name));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, Object>>> getTrending() {
        return ResponseEntity.ok(marketAnalysisService.getTrending());
    }

    @GetMapping("/quote")
    public ResponseEntity<Map<String, Object>> getQuote(@RequestParam String symbol) {
        // Keeping Finnhub for live price as requested
        return ResponseEntity.ok(finnhubService.getStockQuote(symbol));
    }

    @GetMapping("/quotes")
    public ResponseEntity<List<Map<String, Object>>> getQuotes(@RequestParam List<String> symbols) {
        List<Map<String, Object>> results = symbols.stream()
                .map(finnhubService::getStockQuote)
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/news")
    public ResponseEntity<List<Map<String, Object>>> getNews(@RequestParam(defaultValue = "stock market") String query) {
        // Using NewsAPI for real stock-related news
        return ResponseEntity.ok(newsService.getStockNews(query));
    }

    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> getDetails(@RequestParam String symbol) {
        // Enhanced details with mock financials for the Groww look
        Map<String, Object> quote = finnhubService.getStockQuote(symbol);
        Map<String, Object> details = new HashMap<>(quote);
        
        // Mock financial data (In a real app, these would come from an API like FMP)
        details.put("marketCap", "2.85T");
        details.put("peRatio", "32.4");
        details.put("dividendYield", "0.65%");
        details.put("revenue", "383.93B");
        details.put("high52", 199.62);
        details.put("low52", 164.08);
        
        return ResponseEntity.ok(details);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam String q) {
        return ResponseEntity.ok(finnhubService.searchStocks(q));
    }

    @GetMapping("/chart")
    public ResponseEntity<List<Map<String, Object>>> getChartData(@RequestParam String symbol) {
        // Using Yahoo Finance for historical chart data
        return ResponseEntity.ok(yahooService.getHistoricalData(symbol));
    }

    @GetMapping("/indices")
    public ResponseEntity<List<Map<String, Object>>> getIndices() {
        List<String> symbols = List.of("SPY", "QQQ", "DIA");
        List<Map<String, Object>> results = symbols.stream()
                .map(finnhubService::getStockQuote)
                .toList();
        return ResponseEntity.ok(results);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        // Return 503 Service Unavailable or 404 depending on error
        return ResponseEntity.status(503).body(error);
    }
}
