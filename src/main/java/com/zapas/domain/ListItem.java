package com.zapas.domain;

import java.util.UUID;

public record ListItem(UUID id, String name, boolean done, UUID stockItemId, UUID productId, String source,
                       ListDetails details) {}
