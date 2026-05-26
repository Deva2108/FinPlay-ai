package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChartService {
    private final StockHistoryRepo historyRepo;
    private final SymbolNormalizer symbolNormalizer;
    private final StockUniverseRepo universeRepo;

    /**
     * Returns real stored price history from stock_history.
     *
     * No synthetic/random-walk generation. History accumulates naturally as the
     * scheduler calls MarketGateway.updateMirror() during market hours.
     * For newly-tracked symbols the dataset will be small initially but will
     * grow to a meaningful series within days of first observation.
     *
     * The ChartComponent already handles empty datasets gracefully (shows a
     * "Waiting on price history" placeholder), so returning an empty list is
     * always safe.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChartData(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        LocalDate cutoff = calculateCutoff(range);

        List<StockHistory> history = historyRepo.findBySymbolAndDateAfterOrderByDateAsc(normalized, cutoff);

        String currency = universeRepo.findBySymbol(normalized)
                .map(StockUniverse::getCurrency)
                .orElse("INR");

        List<Map<String, Object>> data = history.stream()
                .filter(h -> !h.isSimulated()) // exclude any pre-existing synthetic rows
                .map(h -> {
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
        result.put("source", data.isEmpty() ? "no_history" : "db");
        result.put("points", data.size());

        return result;
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
