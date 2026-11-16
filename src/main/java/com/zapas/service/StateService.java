package com.zapas.service;

import com.zapas.context.CurrentContext;
import com.zapas.domain.NotFoundException;
import com.zapas.repo.EventRepository;
import com.zapas.repo.HouseholdRepository;
import com.zapas.repo.ListRepository;
import com.zapas.repo.StockRepository;
import com.zapas.web.Dto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Собирает «состояние» ровно той формы, что фронт раньше хранил в localStorage (объект S). */
@Service
public class StateService {

    /** События, которые клиент вправе писать сам (остальные порождает бэкенд). */
    private static final Set<String> CLIENT_EVENTS = Set.of("reminder_opened");

    private final CurrentContext ctx;
    private final AppClock clock;
    private final ReminderService reminders;
    private final StockRepository stock;
    private final ListRepository lists;
    private final EventRepository events;
    private final HouseholdRepository households;

    public StateService(CurrentContext ctx, AppClock clock, ReminderService reminders, StockRepository stock,
                        ListRepository lists, EventRepository events, HouseholdRepository households) {
        this.ctx = ctx;
        this.clock = clock;
        this.reminders = reminders;
        this.stock = stock;
        this.lists = lists;
        this.events = events;
        this.households = households;
    }

    @Transactional
    public Dto.State snapshot() {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        List<String> fresh = reminders.check(h, now);

        List<Dto.Item> items = stock.findActive(h).stream()
                .map(i -> new Dto.Item(i.id(), i.name(), i.total(), i.start().toEpochMilli(), i.thr(),
                        i.notifiedAt() != null))
                .toList();
        List<Dto.ListEntry> list = lists.findAll(h).stream()
                .map(l -> new Dto.ListEntry(l.id(), l.name(), l.done(), l.stockItemId(), l.source()))
                .toList();
        Dto.Log log = new Dto.Log(events.surprises(h),
                events.count(h, "reminder_shown"),
                events.count(h, "bought_early"));

        return new Dto.State(clock.offsetDays(h), items, list, log, fresh);
    }

    @Transactional
    public void setOffset(int days) {
        households.setOffsetDays(ctx.householdId(), days);
    }

    @Transactional
    public void logClientEvent(String type, UUID stockItemId, Map<String, Object> props) {
        if (!CLIENT_EVENTS.contains(type)) {
            throw new IllegalArgumentException("Недопустимый тип события: " + type);
        }
        UUID h = ctx.householdId();
        if (stockItemId != null && stock.find(h, stockItemId).isEmpty()) {
            throw new NotFoundException("Товар не найден");
        }
        events.insert(h, ctx.userId(), stockItemId, null, type, props, clock.now(h));
    }

    @Transactional
    public void reset() {
        households.wipe(ctx.householdId());
    }
}
