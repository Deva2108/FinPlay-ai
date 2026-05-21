package com.example.stockPortfolio.MarketManagement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
@Slf4j
public class MarketStatusService {

    private static final ZoneId US_ZONE = ZoneId.of("America/New_York");
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    public boolean isUsMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(US_ZONE);
        return isMarketOpen(now, LocalTime.of(9, 30), LocalTime.of(16, 0));
    }

    public boolean isIndianMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(INDIA_ZONE);
        return isMarketOpen(now, LocalTime.of(9, 15), LocalTime.of(15, 30));
    }
    
    public boolean isUsPreMarket() {
        ZonedDateTime now = ZonedDateTime.now(US_ZONE);
        // 2 hours before 9:30 AM
        return isMarketOpen(now, LocalTime.of(7, 30), LocalTime.of(9, 30));
    }

    public boolean isIndianPreMarket() {
        ZonedDateTime now = ZonedDateTime.now(INDIA_ZONE);
        // 2 hours before 9:15 AM
        return isMarketOpen(now, LocalTime.of(7, 15), LocalTime.of(9, 15));
    }

    private boolean isMarketOpen(ZonedDateTime now, LocalTime open, LocalTime close) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(open) && !time.isAfter(close);
    }

    /** Returns true if ANY market is currently open OR in its 2-hour pre-market warm-up phase. */
    public boolean isAnyMarketActive() {
        return isUsMarketOpen() || isIndianMarketOpen() || isUsPreMarket() || isIndianPreMarket();
    }
    
    public boolean isAnyMarketOpen() {
        return isUsMarketOpen() || isIndianMarketOpen();
    }
}
