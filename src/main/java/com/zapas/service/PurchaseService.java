package com.zapas.service;

import com.zapas.config.AppProperties;
import com.zapas.context.CurrentContext;
import com.zapas.domain.ListItem;
import com.zapas.domain.StockItem;
import com.zapas.repo.EventRepository;
import com.zapas.repo.ListRepository;
import com.zapas.repo.PurchaseRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Фиксация покупки: статус позиции списка + запись в истории покупок + событие. */
@Service
public class PurchaseService {

    private final CurrentContext ctx;
    private final AppProperties props;
    private final ListRepository lists;
    private final PurchaseRepository purchases;
    private final EventRepository events;

    public PurchaseService(CurrentContext ctx, AppProperties props, ListRepository lists,
                           PurchaseRepository purchases, EventRepository events) {
        this.ctx = ctx;
        this.props = props;
        this.lists = lists;
        this.purchases = purchases;
        this.events = events;
    }

    public void recordListPurchase(UUID h, ListItem li, Instant now) {
        lists.setPurchased(li.id(), ctx.userId(), now);
        purchases.insert(h, li.productId(), li.id(), li.name(), props.defaultCurrency(),
                ctx.userId(), now, "list");
        events.insert(h, ctx.userId(), li.stockItemId(), li.id(), "item_purchased",
                Map.of("source", li.source()), now);
    }

    public void recordManualPurchase(UUID h, StockItem item, Instant now) {
        purchases.insert(h, item.productId(), null, item.name(), props.defaultCurrency(),
                ctx.userId(), now, "manual");
        events.insert(h, ctx.userId(), item.id(), null, "item_purchased",
                Map.of("source", "manual"), now);
    }
}
