package com.zapas.context;

import com.zapas.domain.UnauthorizedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Кто сейчас делает запрос. Пользователя кладёт в контекст AuthInterceptor (по cookie сессии)
 * и убирает после запроса. Сервисы получают userId/householdId отсюда, поэтому каждый
 * видит только данные своего домохозяйства.
 */
@Component
public class CurrentContext {

    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    public void set(AuthUser user) {
        CURRENT.set(user);
    }

    public void clear() {
        CURRENT.remove();
    }

    public AuthUser user() {
        AuthUser u = CURRENT.get();
        if (u == null) throw new UnauthorizedException("Требуется вход");
        return u;
    }

    public UUID userId() {
        return user().userId();
    }

    public UUID householdId() {
        return user().householdId();
    }
}
