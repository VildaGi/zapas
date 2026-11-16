package com.zapas.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Товар на складе дома. name берётся из products, total — expected_duration_days,
 * thr — reminder_before_days, start — last_restocked_at.
 */
public record StockItem(UUID id, UUID productId, String name, int total,
                        Instant start, int thr, Instant notifiedAt) {

    public StockItem withStart(Instant newStart) {
        return new StockItem(id, productId, name, total, newStart, thr, notifiedAt);
    }

    public StockItem withNotifiedAt(Instant at) {
        return new StockItem(id, productId, name, total, start, thr, at);
    }

    public StockItem withSettings(int newTotal, int newThr) {
        return new StockItem(id, productId, name, newTotal, start, newThr, notifiedAt);
    }

    public Instant runoutAt() {
        return start.plus(total, ChronoUnit.DAYS);
    }

    public int daysLeft(Instant now) {
        return StockMath.daysLeft(total, start, now);
    }

    public StockMath.Status status(Instant now) {
        return StockMath.status(total, thr, start, now);
    }
}
