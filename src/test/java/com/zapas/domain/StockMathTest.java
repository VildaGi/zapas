package com.zapas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class StockMathTest {

    private static final Instant NOW = Instant.parse("2026-01-31T12:00:00Z");

    @Test
    void daysLeftRoundsUp() {
        Instant start = NOW.minus(28, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS);
        assertEquals(2, StockMath.daysLeft(30, start, NOW));
    }

    @Test
    void statusOkSoonOut() {
        assertEquals(StockMath.Status.OK, StockMath.status(30, 3, NOW.minus(10, ChronoUnit.DAYS), NOW));
        assertEquals(StockMath.Status.SOON, StockMath.status(30, 3, NOW.minus(28, ChronoUnit.DAYS), NOW));
        assertEquals(StockMath.Status.OUT, StockMath.status(30, 3, NOW.minus(30, ChronoUnit.DAYS), NOW));
    }

    @Test
    void suddenMarksItemAsOut() {
        StockItem it = new StockItem(null, null, "Кофе", 14, NOW.minus(14, ChronoUnit.DAYS), 2, NOW);
        assertEquals(StockMath.Status.OUT, it.status(NOW));
    }
}
