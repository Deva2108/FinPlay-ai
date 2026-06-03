package com.example.stockPortfolio.AlertManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import com.example.stockPortfolio.MarketManagement.MarketGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Removed @Lazy(false) to prevent blocking startup.
// Constructor manually written (not @RequiredArgsConstructor) so we can
// @Lazy-inject the repos + gateway. Without @Lazy here, AlertService and the
// scheduling infra eagerly pulled JPA repos at context refresh, which in turn
// pulled the EntityManagerFactory back onto the critical path — defeating the
// `bootstrap-mode=deferred` setting.
@Service
@Slf4j
public class AlertService {

    private final AlertRepo alertRepo;
    private final AlertHistoryRepo historyRepo;
    private final MarketGateway marketGateway;

    public AlertService(@Lazy AlertRepo alertRepo,
                        @Lazy AlertHistoryRepo historyRepo,
                        @Lazy MarketGateway marketGateway) {
        this.alertRepo = alertRepo;
        this.historyRepo = historyRepo;
        this.marketGateway = marketGateway;
    }

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

    /**
     * READ ONLY FROM MIRROR.
     * This engine no longer triggers external API calls.
     */
    // initialDelay = 90 s so the FIRST tick lands well after Tomcat has bound
    // the port and the JPA EMF has finished initializing on its deferred thread.
    // Without an initialDelay, this can fire during the last seconds of context
    // refresh and yank the alert repos onto the boot critical path.
    @Scheduled(fixedRate = 180000, initialDelay = 90000) // 3 mins, first run +90 s
    public void runSmartAlertEngine() {
        List<String> symbols = alertRepo.findAllDistinctSymbols();
        if (symbols.isEmpty()) return;

        Map<String, Map<String, Object>> quotes = marketGateway.getBatchQuotes(symbols);

        // Process in batches or by symbol to avoid large heap usage
        LocalDateTime cooldownCutoff = LocalDateTime.now().minusMinutes(COOLDOWN_MINUTES);
        for (String symbol : symbols) {
            Map<String, Object> quote = quotes.get(symbol);
            if (quote == null || !quote.containsKey("price")) continue;

            double currentPrice = Double.parseDouble(quote.get("price").toString());
            List<Alert> alerts = alertRepo.findActiveBySymbol(symbol, cooldownCutoff);

            for (Alert alert : alerts) {
                try {
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

    private void processAutoAlerts(Alert alert, Map<String, Object> quote, double currentPrice) {
        // High/Low alerts only trigger if 52-week data is available in the mirror
        if (quote.containsKey("yearHigh") && quote.containsKey("yearLow")) {
            double yearHigh = Double.parseDouble(quote.get("yearHigh").toString());
            double yearLow = Double.parseDouble(quote.get("yearLow").toString());

            if (currentPrice >= (yearHigh * 0.98)) {
                trigger(alert, "Near 52W High", "🚀 " + alert.getSymbol() + " is soaring near its yearly high: " + yearHigh);
                return;
            } else if (currentPrice <= (yearLow * 1.02)) {
                trigger(alert, "Near 52W Low", "📉 " + alert.getSymbol() + " is at a yearly low discount: " + yearLow);
                return;
            }
        }

        // Percentage swing alerts (Data always available in the mirror)
        if (quote.containsKey("changesPercentage")) {
            double changePercent = Double.parseDouble(quote.get("changesPercentage").toString());
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
        // Validate stock exists in our mirrored universe
        ApiResponse<Map<String, Object>> response = marketGateway.getLatestQuote(alert.getSymbol());
        Map<String, Object> quote = response.getData();

        if (quote != null) {
            alert.setLastTriggeredAt(null);
            alertRepo.save(alert);
            marketGateway.markAsPriority(alert.getSymbol());

            AlertDTO dto = new AlertDTO();
            dto.setMessage("Alert Set for " + alert.getSymbol() + " 🔔");
            dto.setLocalDateTime(LocalDateTime.now());
            dto.setStatus(200);
            return dto;
        } else {
            throw new RuntimeException("Stock symbol not found in our market universe or feed is syncing.");
        }
    }
}
