package com.example.stockPortfolio.MarketManagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForexQuoteDTO {
    private String base;       // e.g. USD
    private String quote;      // e.g. INR
    private BigDecimal rate;   // 1 base = rate quote
    private LocalDateTime asOf;
}
