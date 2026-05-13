package com.example.stockPortfolio.MarketManagement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The set of symbols pre-warmed nightly into the historical chart cache
 * (1W/1M/1Y/5Y). Top 50 US + top 50 India by market cap / liquidity.
 *
 * Override at runtime via env:
 *   HISTORICAL_UNIVERSE_US     -- comma-separated, replaces defaults
 *   HISTORICAL_UNIVERSE_INDIA  -- comma-separated, replaces defaults
 *   HISTORICAL_UNIVERSE_EXTRA  -- comma-separated, ADDED to defaults
 */
@Component
public class HistoricalChartUniverse {

    private static final List<String> DEFAULT_US = List.of(
            "AAPL","MSFT","GOOGL","AMZN","NVDA","META","TSLA","BRK-B","JPM","V",
            "UNH","XOM","JNJ","WMT","PG","MA","AVGO","HD","CVX","MRK",
            "LLY","KO","PEP","ABBV","BAC","COST","ADBE","NFLX","DIS","CSCO",
            "ORCL","CRM","TMO","ACN","MCD","NKE","ABT","AMD","INTC","IBM",
            "QCOM","GE","F","GM","BA","CAT","HON","GS","UBER","PYPL"
    );

    /** Indian symbols stored in canonical Yahoo form (.NS suffix). */
    private static final List<String> DEFAULT_INDIA = List.of(
            "RELIANCE.NS","TCS.NS","HDFCBANK.NS","INFY.NS","ICICIBANK.NS",
            "HINDUNILVR.NS","SBIN.NS","BAJFINANCE.NS","BHARTIARTL.NS","ITC.NS",
            "KOTAKBANK.NS","LT.NS","ASIANPAINT.NS","AXISBANK.NS","MARUTI.NS",
            "HCLTECH.NS","WIPRO.NS","ULTRACEMCO.NS","NESTLEIND.NS","TITAN.NS",
            "ADANIPORTS.NS","ADANIENT.NS","JSWSTEEL.NS","NTPC.NS","POWERGRID.NS",
            "M&M.NS","ONGC.NS","COALINDIA.NS","TATAMOTORS.NS","TATASTEEL.NS",
            "SUNPHARMA.NS","DRREDDY.NS","CIPLA.NS","DIVISLAB.NS","EICHERMOT.NS",
            "HEROMOTOCO.NS","BAJAJ-AUTO.NS","BPCL.NS","IOC.NS","GAIL.NS",
            "GRASIM.NS","INDUSINDBK.NS","BRITANNIA.NS","HDFCLIFE.NS","SBILIFE.NS",
            "BAJAJFINSV.NS","TECHM.NS","UPL.NS","APOLLOHOSP.NS","TATACONSUM.NS",
            "ZOMATO.NS"
    );

    @Value("${historical.universe.us:}")
    private String usOverride;

    @Value("${historical.universe.india:}")
    private String inOverride;

    @Value("${historical.universe.extra:}")
    private String extraOverride;

    public List<String> all() {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(parseOrDefault(usOverride, DEFAULT_US));
        out.addAll(parseOrDefault(inOverride, DEFAULT_INDIA));
        if (extraOverride != null && !extraOverride.isBlank()) {
            out.addAll(splitCsv(extraOverride));
        }
        return List.copyOf(out);
    }

    private static List<String> parseOrDefault(String csv, List<String> fallback) {
        if (csv == null || csv.isBlank()) return fallback;
        return splitCsv(csv);
    }

    private static List<String> splitCsv(String csv) {
        List<String> out = new java.util.ArrayList<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t.toUpperCase());
        }
        return Collections.unmodifiableList(out);
    }
}
