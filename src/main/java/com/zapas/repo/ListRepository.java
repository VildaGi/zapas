package com.zapas.repo;

import com.zapas.domain.ListItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ListRepository {

    private static final String SELECT = """
            select li.id, li.name, (li.status = 'purchased') as done,
                   li.stock_item_id, li.product_id, li.source
              from list_items li
              join shopping_lists sl on sl.id = li.list_id
             where sl.household_id = :h and not sl.is_archived and li.status <> 'cancelled'
            """;

    private static final RowMapper<ListItem> MAPPER = (rs, i) -> new ListItem(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getBoolean("done"),
            rs.getObject("stock_item_id", UUID.class),
            rs.getObject("product_id", UUID.class),
            rs.getString("source"));

    private final JdbcClient jdbc;

    public ListRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID defaultListId(UUID h, UUID userId) {
        return jdbc.sql("""
                select id from shopping_lists
                 where household_id = :h and not is_archived
                 order by created_at limit 1
                """).param("h", h).query(UUID.class).optional()
                .orElseGet(() -> jdbc.sql("insert into shopping_lists(household_id, created_by) values (:h, :u) returning id")
                        .param("h", h).param("u", userId).query(UUID.class).single());
    }

    public List<ListItem> findAll(UUID h) {
        return jdbc.sql(SELECT + " order by li.created_at, li.id").param("h", h).query(MAPPER).list();
    }

    public Optional<ListItem> find(UUID h, UUID id) {
        return jdbc.sql(SELECT + " and li.id = :id").param("h", h).param("id", id).query(MAPPER).optional();
    }

    public List<ListItem> findOpenByStock(UUID h, UUID stockItemId) {
        return jdbc.sql(SELECT + " and li.stock_item_id = :s and li.status = 'needed' order by li.created_at")
                .param("h", h).param("s", stockItemId).query(MAPPER).list();
    }

    public boolean existsOpenForStock(UUID stockItemId) {
        return jdbc.sql("select exists(select 1 from list_items where stock_item_id = :s and status = 'needed')")
                .param("s", stockItemId).query(Boolean.class).single();
    }

    public UUID insert(UUID listId, UUID productId, UUID stockItemId, String name, String source, UUID userId) {
        return jdbc.sql("""
                insert into list_items(list_id, product_id, stock_item_id, name, source, added_by)
                values (:l, :p, :s, :n, :src, :u)
                returning id
                """)
                .param("l", listId).param("p", productId).param("s", stockItemId)
                .param("n", name).param("src", source).param("u", userId)
                .query(UUID.class).single();
    }

    public void setPurchased(UUID id, UUID userId, Instant at) {
        jdbc.sql("""
                update list_items
                   set status = 'purchased', purchased_by = :u, purchased_at = :at, updated_at = now()
                 where id = :id
                """).param("u", userId).param("at", Db.ts(at)).param("id", id).update();
    }

    public void setNeeded(UUID id) {
        jdbc.sql("""
                update list_items
                   set status = 'needed', purchased_by = null, purchased_at = null, updated_at = now()
                 where id = :id
                """).param("id", id).update();
    }

    public void rename(UUID id, String name) {
        jdbc.sql("update list_items set name = :n, updated_at = now() where id = :id")
                .param("n", name).param("id", id).update();
    }

    public void delete(UUID id) {
        jdbc.sql("delete from list_items where id = :id").param("id", id).update();
    }

    public void clearPurchased(UUID h) {
        jdbc.sql("""
                delete from list_items li
                 using shopping_lists sl
                 where sl.id = li.list_id and sl.household_id = :h and li.status = 'purchased'
                """).param("h", h).update();
    }
}
