package com.zapas.service;

import com.zapas.repo.HouseholdRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** «Текущее время» с учётом тестового смещения в днях (кнопка «+1 день»). */
@Component
public class AppClock {

    private final HouseholdRepository households;

    public AppClock(HouseholdRepository households) {
        this.households = households;
    }

    public int offsetDays(UUID h) {
        return households.offsetDays(h);
    }

    public Instant now(UUID h) {
        return Instant.now().plus(offsetDays(h), ChronoUnit.DAYS);
    }
}
