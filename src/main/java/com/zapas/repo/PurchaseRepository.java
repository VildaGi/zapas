package com.zapas.repo;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseRepository {

    private final JdbcClient jdbc;

    public PurchaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID h, UUID productId, UUID listItemId, String productName,
                       String currency, UUID userId, Instant at, String source) {
        jdbc.sql("""
                insert into purchases(household_id, product_id, list_item_id, product_name,
                                      currency, purchased_by, purchased_at, source)
                values (:h, :p, :li, :n, :c, :u, :at, :src)
                """)
                .param("h", h).param("p", productId).param("li", listItemId)
                .param("n", productName).param("c", currency).param("u", userId)
                .param("at", Db.ts(at)).param("src", source)
                .update();
    }

    public void deleteByListItem(UUID listItemId) {
        jdbc.sql("delete from purchases where list_item_id = :id").param("id", listItemId).update();
    }
}
