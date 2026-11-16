package com.zapas.service;

import com.zapas.repo.HouseholdRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

    private final HouseholdRepository households;

    public HouseholdService(HouseholdRepository households) {
        this.households = households;
    }

    /** Находит домохозяйство пользователя или создаёт «Мой дом». */
    @Transactional
    public UUID ensureFor(UUID userId) {
        households.ensureProfile(userId);
        return households.findByUser(userId).orElseGet(() -> households.create("Мой дом", userId));
    }
}
