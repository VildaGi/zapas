package com.zapas.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String defaultCurrency, List<String> corsAllowedOrigins, Integer sessionDays) {

    public List<String> corsOrigins() {
        return corsAllowedOrigins == null ? List.of() : corsAllowedOrigins.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public int sessionDaysOrDefault() {
        return sessionDays == null || sessionDays < 1 ? 30 : sessionDays;
    }
}
