package com.example.stockPortfolio.MarketManagement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    @Cacheable(value = "stockCharts", key = "#symbol + '_' + #range")
    @Transactional(readOnly = true)
    public Map<String, Object> getChartData(String symbol, String range) {
        String normalized = symbolNormalizer.normalize(symbol);
        LocalDate cutoff = calculateCutoff(range);

        List<StockHistory> history = historyRepo.findBySymbolAndDateAfterOrderByDateAsc(normalized, cutoff);

        String currency = universeRepo.findBySymbol(normalized)
                .map(StockUniverse::getCurrency)
                .orElse("INR");

        // Single filtered source of truth — drives both the point series and the
        // period OHLC, so the chart line and the OHLC strip can never disagree.
        List<StockHistory> rows = history.stream()
                .filter(h -> !h.isSimulated()) // exclude any pre-existing synthetic rows
                .collect(Collectors.toList());

        List<Map<String, Object>> data = rows.stream()
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

        // FIX 3 — period OHLC computed server-side from the real daily closes
        // (open = first close, close = last close, high/low = extremes). Per-row
        // intraday OHLC is not captured in stock_history, so these are close-based
        // by design — the same values the client used to derive, now authoritative
        // on the backend. Omitted entirely when there is no history.
        if (!rows.isEmpty()) {
            BigDecimal first = rows.get(0).getClose();
            BigDecimal last = rows.get(rows.size() - 1).getClose();
            BigDecimal hi = first, lo = first;
            for (StockHistory h : rows) {
                BigDecimal c = h.getClose();
                if (c.compareTo(hi) > 0) hi = c;
                if (c.compareTo(lo) < 0) lo = c;
            }
            result.put("open", first.setScale(2, RoundingMode.HALF_UP));
            result.put("high", hi.setScale(2, RoundingMode.HALF_UP));
            result.put("low", lo.setScale(2, RoundingMode.HALF_UP));
            result.put("close", last.setScale(2, RoundingMode.HALF_UP));
        }

        return result;
    }

    private LocalDate calculateCutoff(String range) {
        return switch (range.toUpperCase()) {
            case "1D" -> LocalDate.now().minusDays(1);
            case "1W" -> LocalDate.now().minusWeeks(1);
            case "1M" -> LocalDate.now().minusMonths(1);
            case "6M" -> LocalDate.now().minusMonths(6);
            case "1Y" -> LocalDate.now().minusYears(1);
            default   -> LocalDate.now().minusYears(3);
        };
    }
}
