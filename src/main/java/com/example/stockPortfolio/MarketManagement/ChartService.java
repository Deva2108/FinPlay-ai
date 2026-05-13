package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChartService {
    private final StockHistoryRepo historyRepo;
    private final MarketGateway marketGateway;
    private final SymbolNormalizer symbolNormalizer;
    private final StockUniverseRepo universeRepo;

    @Transactional
    public Map<String, Object> getChartData(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        LocalDate cutoff = calculateCutoff(range);
        
        // 1. Ensure minimal data exists
        if (!historyRepo.existsBySymbol(normalized)) {
            generateSyntheticHistory(normalized);
        }

        // 2. Fetch from DB
        List<StockHistory> history = historyRepo.findBySymbolAndDateAfterOrderByDateAsc(normalized, cutoff);

        // 3. Get currency from Universe
        String currency = universeRepo.findBySymbol(normalized)
                .map(StockUniverse::getCurrency)
                .orElse("INR");

        // 4. Map to response
        List<Map<String, Object>> data = history.stream().map(h -> {
            Map<String, Object> point = new HashMap<>();
            point.put("date", h.getDate().toString());
            point.put("close", h.getClose().setScale(2, RoundingMode.HALF_UP));
            point.put("value", h.getClose().setScale(2, RoundingMode.HALF_UP));
            return point;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", normalized);
        result.put("currency", currency);
        result.put("data", data);
        
        return result;
    }

    private void generateSyntheticHistory(String symbol) {
        // Anchor price is always INR from getLatestQuote SSOT
        ApiResponse<Map<String, Object>> quote = marketGateway.getLatestQuote(symbol);
        BigDecimal anchorPrice = (quote.getData() != null && quote.getData().get("price") != null) 
            ? new BigDecimal(quote.getData().get("price").toString()) 
            : new BigDecimal("100.00");
        
        List<StockHistory> synthetic = new ArrayList<>();
        Random random = new Random(symbol.hashCode()); 
        
        BigDecimal current = anchorPrice;
        for (int i = 0; i < 750; i++) { // ~3Y of trading days
            LocalDate date = LocalDate.now().minusDays(i);
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            
            synthetic.add(StockHistory.builder()
                .symbol(symbol)
                .date(date)
                .close(current.setScale(4, RoundingMode.HALF_UP))
                .isSimulated(true)
                .build());
            
            double change = (random.nextDouble() * 0.03) - 0.0145;
            current = current.divide(BigDecimal.valueOf(1 + change), 4, RoundingMode.HALF_UP);
        }
        saveNonDuplicateHistory(synthetic);
    }

    private void saveNonDuplicateHistory(List<StockHistory> data) {
        if (data.isEmpty()) return;
        String symbol = data.get(0).getSymbol();
        Set<LocalDate> existingDates = historyRepo.findBySymbolAndDateAfterOrderByDateAsc(symbol, LocalDate.now().minusYears(4))
                .stream().map(StockHistory::getDate).collect(Collectors.toSet());
        
        List<StockHistory> toSave = data.stream()
                .filter(h -> !existingDates.contains(h.getDate()))
                .toList();
                
        historyRepo.saveAll(toSave);
    }

    private LocalDate calculateCutoff(String range) {
        return switch (range.toUpperCase()) {
            case "1M" -> LocalDate.now().minusMonths(1);
            case "6M" -> LocalDate.now().minusMonths(6);
            case "1Y" -> LocalDate.now().minusYears(1);
            default -> LocalDate.now().minusYears(3);
        };
    }
}
