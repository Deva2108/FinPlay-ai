package com.example.stockPortfolio.MarketManagement;

import com.example.stockPortfolio.HoldingsManagement.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Currency endpoints for the FinPlay USD↔INR converter. */
@RestController
@RequestMapping("/api/forex")
@Tag(name = "11. Forex", description = "USD↔INR rates and conversions")
@RequiredArgsConstructor
public class ForexController {

    private final ForexService forexService;

    @GetMapping("/usd-inr")
    public ResponseEntity<ApiResponse<ForexQuoteDTO>> getUsdInr() {
        ForexQuoteDTO quote = forexService.getCachedUsdInr();
        if (quote == null) {
            return ResponseEntity.ok(ApiResponse.syncing(null, "USD→INR rate is syncing", "fallback"));
        }
        return ResponseEntity.ok(ApiResponse.ok(quote, "Rate fetched from cache", "cache"));
    }

    @GetMapping("/convert")
    public ResponseEntity<ApiResponse<Map<String, Object>>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "USD") String from,
            @RequestParam(defaultValue = "INR") String to) {

        BigDecimal converted;
        if ("USD".equalsIgnoreCase(from) && "INR".equalsIgnoreCase(to)) {
            converted = forexService.convertUsdToInr(amount);
        } else if ("INR".equalsIgnoreCase(from) && "USD".equalsIgnoreCase(to)) {
            converted = forexService.convertInrToUsd(amount);
        } else {
            return ResponseEntity.ok(ApiResponse.error("Only USD↔INR is supported in this build"));
        }

        if (converted == null) {
            return ResponseEntity.ok(ApiResponse.syncing(
                    Map.of("amount", amount, "from", from, "to", to),
                    "Forex rate not yet available",
                    "fallback"));
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "amount", amount,
                "from", from,
                "to", to,
                "converted", converted
        ), "Converted using cached rate", "cache"));
    }
}
