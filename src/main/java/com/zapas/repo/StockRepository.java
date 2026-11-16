package com.zapas.repo;

import com.zapas.domain.StockItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {

    private static final String SELECT = """
            select s.id, s.product_id, p.name,
                   coalesce(s.expected_duration_days, 30) as total,
                   coalesce(s.last_restocked_at, s.created_at) as start,
                   s.reminder_before_days as thr,
                   s.notified_at
              from stock_items s
              join products p on p.id = s.product_id
             where s.household_id = :h and s.is_active
            """;

    private static final RowMapper<StockItem> MAPPER = (rs, i) -> new StockItem(
            rs.getObject("id", UUID.class),
            rs.getObject("product_id", UUID.class),
            rs.getString("name"),
            rs.getInt("total"),
            Db.instant(rs.getObject("start", OffsetDateTime.class)),
            rs.getInt("thr"),
            Db.instant(rs.getObject("notified_at", OffsetDateTime.class)));

    private final JdbcClient jdbc;

    public StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StockItem> findActive(UUID h) {
        return jdbc.sql(SELECT + " order by s.created_at, s.id")
                .param("h", h).query(MAPPER).list();
    }

    public Optional<StockItem> find(UUID h, UUID id) {
        return jdbc.sql(SELECT + " and s.id = :id")
                .param("h", h).param("id", id).query(MAPPER).optional();
    }

    public UUID insertProduct(UUID h, String name) {
        return jdbc.sql("insert into products(name, household_id) values (:n, :h) returning id")
                .param("n", name).param("h", h).query(UUID.class).single();
    }

    public void renameProduct(UUID h, UUID productId, String name) {
        jdbc.sql("update products set name = :n, updated_at = now() where id = :p and household_id = :h")
                .param("n", name).param("p", productId).param("h", h).update();
    }

    public UUID insertStock(UUID h, UUID userId, UUID productId, int total, int thr, Instant start) {
        return jdbc.sql("""
                insert into stock_items(household_id, product_id, expected_duration_days,
                                        reminder_before_days, last_restocked_at,
                                        estimated_runout_at, created_by)
                values (:h, :p, :total, :thr, :start, :runout, :u)
                returning id
                """)
                .param("h", h).param("p", productId).param("total", total).param("thr", thr)
                .param("start", Db.ts(start))
                .param("runout", Db.ts(start.plus(total, java.time.temporal.ChronoUnit.DAYS)))
                .param("u", userId)
                .query(UUID.class).single();
    }

    public void save(UUID h, StockItem it) {
        jdbc.sql("""
                update stock_items
                   set expected_duration_days = :total,
                       reminder_before_days   = :thr,
                       last_restocked_at      = :start,
                       estimated_runout_at    = :runout,
                       notified_at            = :notified,
                       updated_at             = now()
                 where id = :id and household_id = :h
                """)
                .param("total", it.total()).param("thr", it.thr())
                .param("start", Db.ts(it.start()))
                .param("runout", Db.ts(it.runoutAt()))
                .param("notified", Db.ts(it.notifiedAt()))
                .param("id", it.id()).param("h", h)
                .update();
    }

    /** Мягкое удаление: stock_movements ссылаются на stock_items с on delete restrict. */
    public int deactivate(UUID h, UUID id) {
        return jdbc.sql("update stock_items set is_active = false, updated_at = now() where id = :id and household_id = :h and is_active")
                .param("id", id).param("h", h).update();
    }
}
