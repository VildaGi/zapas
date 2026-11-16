package com.zapas.domain;

import java.time.Instant;

/** Та же математика, что в state.js: daysLeft / status. */
public final class StockMath {

    public static final long DAY_MS = 86_400_000L;

    public enum Status { OK, SOON, OUT }

    private StockMath() {}

    public static int daysLeft(int total, Instant start, Instant now) {
        double used = (now.toEpochMilli() - start.toEpochMilli()) / (double) DAY_MS;
        return (int) Math.ceil(total - used);
    }

    public static Status status(int total, int thr, Instant start, Instant now) {
        int d = daysLeft(total, start, now);
        if (d <= 0) return Status.OUT;
        return d <= thr ? Status.SOON : Status.OK;
    }
}
