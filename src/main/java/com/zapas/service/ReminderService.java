package com.zapas.service;

import com.zapas.context.CurrentContext;
import com.zapas.domain.StockItem;
import com.zapas.domain.StockMath;
import com.zapas.repo.EventRepository;
import com.zapas.repo.ListRepository;
import com.zapas.repo.StockRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private final CurrentContext ctx;
    private final StockRepository stock;
    private final ListRepository lists;
    private final EventRepository events;

    public ReminderService(CurrentContext ctx, StockRepository stock, ListRepository lists, EventRepository events) {
        this.ctx = ctx;
        this.stock = stock;
        this.lists = lists;
        this.events = events;
    }

    /**
     * Аналог check() из action.js: для товаров, у которых запас подходит к концу и напоминание ещё
     * не показывалось, помечаем notified, добавляем позицию в список и пишем событие reminder_shown.
     *
     * @return названия товаров, по которым напоминание сработало прямо сейчас
     */
    @Transactional
    public List<String> check(UUID h, Instant now) {
        List<String> fresh = new ArrayList<>();
        UUID listId = null;
        for (StockItem it : stock.findActive(h)) {
            if (it.status(now) == StockMath.Status.OK || it.notifiedAt() != null) continue;

            stock.save(h, it.withNotifiedAt(now));
            if (!lists.existsOpenForStock(it.id())) {
                if (listId == null) listId = lists.defaultListId(h, ctx.userId());
                lists.insert(listId, it.productId(), it.id(), it.name(), "reminder", ctx.userId());
            }
            events.insert(h, ctx.userId(), it.id(), null, "reminder_shown", Map.of("name", it.name()), now);
            fresh.add(it.name());
        }
        return fresh;
    }
}
