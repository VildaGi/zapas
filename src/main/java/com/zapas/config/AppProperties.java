package com.zapas.config;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(UUID defaultUserId, String defaultCurrency, List<String> corsAllowedOrigins) {

    public List<String> corsOrigins() {
        return corsAllowedOrigins == null ? List.of() : corsAllowedOrigins.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
