package com.zapas.context;

import com.zapas.config.AppProperties;
import com.zapas.service.HouseholdService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Кто сейчас делает запрос. Пока фронт без авторизации — это один настроенный пользователь
 * (app.default-user-id) и его домохозяйство. Для мультипользовательского режима достаточно
 * заменить реализацию: брать userId из JWT Supabase, а householdId — из household_members.
 */
@Component
public class CurrentContext {

    private final AppProperties props;
    private final HouseholdService households;
    private volatile UUID householdId;

    public CurrentContext(AppProperties props, HouseholdService households) {
        this.props = props;
        this.households = households;
    }

    public UUID userId() {
        return props.defaultUserId();
    }

    public UUID householdId() {
        UUID id = householdId;
        if (id == null) {
            synchronized (this) {
                if (householdId == null) {
                    householdId = households.ensureFor(userId());
                }
                id = householdId;
            }
        }
        return id;
    }
}
