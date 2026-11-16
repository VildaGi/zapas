package com.zapas.service;

import com.zapas.context.CurrentContext;
import com.zapas.domain.ListItem;
import com.zapas.domain.NotFoundException;
import com.zapas.repo.EventRepository;
import com.zapas.repo.ListRepository;
import com.zapas.repo.PurchaseRepository;
import com.zapas.repo.StockRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingListService {

    private final CurrentContext ctx;
    private final AppClock clock;
    private final ListRepository lists;
    private final StockRepository stock;
    private final PurchaseRepository purchaseRepo;
    private final PurchaseService purchases;
    private final EventRepository events;

    public ShoppingListService(CurrentContext ctx, AppClock clock, ListRepository lists, StockRepository stock,
                               PurchaseRepository purchaseRepo, PurchaseService purchases, EventRepository events) {
        this.ctx = ctx;
        this.clock = clock;
        this.lists = lists;
        this.stock = stock;
        this.purchaseRepo = purchaseRepo;
        this.purchases = purchases;
        this.events = events;
    }

    @Transactional
    public void add(String name) {
        UUID h = ctx.householdId();
        String clean = requireName(name);
        UUID listId = lists.defaultListId(h, ctx.userId());
        UUID id = lists.insert(listId, null, null, clean, "manual", ctx.userId());
        events.insert(h, ctx.userId(), null, id, "item_added", Map.of("name", clean), clock.now(h));
    }

    /** PATCH /list/{id}: переименование и/или отметка «куплено» (чекбокс). */
    @Transactional
    public void patch(UUID id, String name, Boolean done) {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        ListItem li = require(h, id);

        if (name != null) {
            lists.rename(id, requireName(name));
        }
        if (done == null || done == li.done()) return;

        if (done) {
            purchases.recordListPurchase(h, li, now);
            if (li.stockItemId() != null) {
                stock.find(h, li.stockItemId()).ifPresent(it -> {
                    if ("reminder".equals(li.source())) {
                        events.insert(h, ctx.userId(), it.id(), li.id(), "bought_early",
                                Map.of("name", it.name()), now);
                    }
                    stock.save(h, it.withStart(now).withNotifiedAt(null));
                });
            }
        } else {
            lists.setNeeded(id);
            purchaseRepo.deleteByListItem(id);
        }
    }

    @Transactional
    public void delete(UUID id) {
        UUID h = ctx.householdId();
        require(h, id);
        lists.delete(id);
    }

    @Transactional
    public void clearPurchased() {
        lists.clearPurchased(ctx.householdId());
    }

    private ListItem require(UUID h, UUID id) {
        return lists.find(h, id).orElseThrow(() -> new NotFoundException("Позиция списка не найдена"));
    }

    private static String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Название не может быть пустым");
        return n;
    }
}
