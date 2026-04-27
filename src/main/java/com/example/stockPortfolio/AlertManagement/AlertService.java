package com.example.stockPortfolio.AlertManagement;

import com.example.stockPortfolio.HoldingsManagement.FmpStockPriceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepo alertRepo;
    private final AlertHistoryRepo historyRepo;
    private final FmpStockPriceService fmpService;

    private static final double RANGE_BUFFER = 0.01; // 1% buffer
    private static final long COOLDOWN_MINUTES = 60; // Don't re-trigger same alert for 1 hour

    public List<AlertDataDTO> getAlertsByUserId(Long userId) {
        return alertRepo.findByUserId(userId).stream()
                .map(AlertDataDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<AlertHistory> getAlertHistoryByUserId(Long userId) {
        return historyRepo.findByUserIdOrderByTriggeredAtDesc(userId);
    }

    @Scheduled(fixedRate = 180000) // 3 mins
    public void runSmartAlertEngine() {
        List<Alert> alerts = alertRepo.findAll();
        if (alerts.isEmpty()) return;

        Set<String> uniqueSymbols = alerts.stream().map(Alert::getSymbol).collect(Collectors.toSet());
        
        // Fetch all quotes in parallel
        List<CompletableFuture<Void>> futures = uniqueSymbols.stream()
            .map(symbol -> CompletableFuture.runAsync(() -> {
                // The cache in fmpService will handle repeated calls efficiently
                fmpService.getFullQuote(symbol);
            }))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (Alert alert : alerts) {
            try {
                if (alert.getLastTriggeredAt() != null && 
                    alert.getLastTriggeredAt().isAfter(LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES))) {
                    continue;
                }

                JsonNode quote = fmpService.getFullQuote(alert.getSymbol());
                if (quote == null || !quote.has("price")) continue;

                double currentPrice = quote.get("price").asDouble();
                
                if (alert.getAlertType() == Alert.AlertType.USER) {
                    processUserAlert(alert, currentPrice);
                } else {
                    processAutoAlerts(alert, quote, currentPrice);
                }
            } catch (Exception e) {
                log.error("Error processing alert for {}: {}", alert.getSymbol(), e.getMessage());
            }
        }
    }

    private void processUserAlert(Alert alert, double currentPrice) {
        if (alert.getTargetPrice() == null) return;
        
        double target = alert.getTargetPrice().doubleValue();
        double margin = target * RANGE_BUFFER;
        
        if (currentPrice >= (target - margin) && currentPrice <= (target + margin)) {
            trigger(alert, "Target Range Reached", 
                    String.format("🎯 Your target for %s is close! Current price: %.2f", alert.getSymbol(), currentPrice));
        }
    }

    private void processAutoAlerts(Alert alert, JsonNode quote, double currentPrice) {
        if (quote.has("yearHigh") && quote.has("yearLow")) {
            double yearHigh = quote.get("yearHigh").asDouble();
            double yearLow = quote.get("yearLow").asDouble();

            if (currentPrice >= (yearHigh * 0.98)) {
                trigger(alert, "Near 52W High", "🚀 " + alert.getSymbol() + " is soaring near its yearly high: " + yearHigh);
                return;
            } else if (currentPrice <= (yearLow * 1.02)) {
                trigger(alert, "Near 52W Low", "📉 " + alert.getSymbol() + " is at a yearly low discount: " + yearLow);
                return;
            }
        }

        if (quote.has("changesPercentage")) {
            double changePercent = quote.get("changesPercentage").asDouble();
            if (changePercent >= 5.0) {
                trigger(alert, "Major Price Swing", "🔥 " + alert.getSymbol() + " is rising fast! (+" + String.format("%.2f", changePercent) + "%)");
            } else if (changePercent <= -5.0) {
                trigger(alert, "Major Price Swing", "⚠️ Market dip detected for " + alert.getSymbol() + " (" + String.format("%.2f", changePercent) + "%)");
            }
        }
    }

    private void trigger(Alert alert, String reason, String message) {
        alert.setTriggerReason(reason);
        alert.setGainOrLoss(message);
        alert.setLastTriggeredAt(LocalDateTime.now());
        alertRepo.save(alert);

        AlertHistory history = new AlertHistory();
        history.setUserId(alert.getUserId());
        history.setSymbol(alert.getSymbol());
        history.setMessage(message);
        history.setAlertType(alert.getAlertType().name());
        history.setTriggeredAt(LocalDateTime.now());
        historyRepo.save(history);

        log.info("ALERT TRIGGERED [{}]: {} for User {}", alert.getSymbol(), reason, alert.getUserId());
    }

    public AlertDTO addAlert(AlertRequestDTO dto, Long userId) {
        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setSymbol(dto.getSymbol().toUpperCase());
        alert.setTargetPrice(dto.getTargetPrice());
        if (dto.getAlertType() != null) {
            alert.setAlertType(Alert.AlertType.valueOf(dto.getAlertType().toUpperCase()));
        }
        return addAlert(alert);
    }

    public AlertDTO addAlert(Alert alert) {
        double currentPrice = fmpService.getStockPrice(alert.getSymbol());

        if (currentPrice > 0) {
            alert.setLastTriggeredAt(null);
            alertRepo.save(alert);

            AlertDTO dto = new AlertDTO();
            dto.setMessage("Alert Set for " + alert.getSymbol() + " 🔔");
            dto.setLocalDateTime(LocalDateTime.now());
            dto.setStatus(200);
            return dto;
        } else {
            throw new RuntimeException("Stock symbol not found or FMP API error.");
        }
    }
}
