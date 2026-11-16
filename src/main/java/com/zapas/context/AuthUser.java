package com.zapas.context;

import java.util.UUID;

/** Пользователь, от имени которого выполняется текущий запрос. */
public record AuthUser(UUID userId, UUID householdId, String email) {}
