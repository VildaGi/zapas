package com.zapas.service;

import com.zapas.context.CurrentContext;
import com.zapas.domain.ListItem;
import com.zapas.domain.NotFoundException;
import com.zapas.domain.StockItem;
import com.zapas.domain.StockMath;
import com.zapas.repo.EventRepository;
import com.zapas.repo.ListRepository;
import com.zapas.repo.StockRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private static final int DEFAULT_TOTAL = 30;

    private final CurrentContext ctx;
    private final AppClock clock;
    private final StockRepository stock;
    private final ListRepository lists;
    private final EventRepository events;
    private final PurchaseService purchases;

    public StockService(CurrentContext ctx, AppClock clock, StockRepository stock, ListRepository lists,
                        EventRepository events, PurchaseService purchases) {
        this.ctx = ctx;
        this.clock = clock;
        this.stock = stock;
        this.lists = lists;
        this.events = events;
        this.purchases = purchases;
    }

    @Transactional
    public void add(String name, Integer total, Integer thr) {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        String cleanName = requireName(name);
        int d = total == null ? DEFAULT_TOTAL : total;
        int t = thr != null ? thr : Math.max(2, (int) Math.round(d * 0.1));

        UUID productId = stock.insertProduct(h, cleanName);
        UUID stockId = stock.insertStock(h, ctx.userId(), productId, d, t, now);
        events.insert(h, ctx.userId(), stockId, null, "item_added", Map.of("name", cleanName), now);
    }

    @Transactional
    public void patch(UUID id, String name, Integer total, Integer thr) {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        StockItem it = require(h, id);

        if (name != null) {
            String cleanName = requireName(name);
            if (!cleanName.equals(it.name())) {
                stock.renameProduct(h, it.productId(), cleanName);
            }
        }
        StockItem updated = it.withSettings(total != null ? total : it.total(), thr != null ? thr : it.thr());
        stock.save(h, updated);
        events.insert(h, ctx.userId(), id, null, "stock_updated", Map.of("action", "edit"), now);
    }

    @Transactional
    public void delete(UUID id) {
        UUID h = ctx.householdId();
        if (stock.deactivate(h, id) == 0) {
            throw new NotFoundException("Товар не найден");
        }
    }

    /** Кнопка «Купил». */
    @Transactional
    public void bought(UUID id) {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        StockItem it = require(h, id);

        if (it.status(now) != StockMath.Status.OK && it.notifiedAt() != null) {
            events.insert(h, ctx.userId(), id, null, "bought_early", Map.of("name", it.name()), now);
        }

        List<ListItem> open = lists.findOpenByStock(h, id);
        for (ListItem li : open) {
            purchases.recordListPurchase(h, li, now);
        }
        if (open.isEmpty()) {
            purchases.recordManualPurchase(h, it, now);
        }

        stock.save(h, it.withStart(now).withNotifiedAt(null));
    }

    /** Кнопка «Закончилось внезапно». */
    @Transactional
    public void sudden(UUID id) {
        UUID h = ctx.householdId();
        Instant now = clock.now(h);
        StockItem it = require(h, id);

        events.insert(h, ctx.userId(), id, null, "ran_out_suddenly", Map.of("name", it.name()), now);
        stock.save(h, it.withStart(now.minus(it.total(), ChronoUnit.DAYS)).withNotifiedAt(now));
        if (!lists.existsOpenForStock(id)) {
            lists.insert(lists.defaultListId(h, ctx.userId()), it.productId(), id, it.name(), "sudden", ctx.userId());
        }
    }

    private StockItem require(UUID h, UUID id) {
        return stock.find(h, id).orElseThrow(() -> new NotFoundException("Товар не найден"));
    }

    private static String requireName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Название не может быть пустым");
        return n;
    }
}
