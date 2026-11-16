package com.zapas.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class Db {
    private Db() {}

    /** pgjdbc не умеет Instant — передаём OffsetDateTime. */
    static OffsetDateTime ts(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
