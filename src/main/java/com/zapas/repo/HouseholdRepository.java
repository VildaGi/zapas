package com.zapas.repo;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HouseholdRepository {

    private final JdbcClient jdbc;

    public HouseholdRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureProfile(UUID userId) {
        jdbc.sql("insert into profiles(id) values (:u) on conflict (id) do nothing")
                .param("u", userId).update();
    }

    public Optional<UUID> findByUser(UUID userId) {
        return jdbc.sql("""
                select household_id from household_members
                 where user_id = :u order by joined_at limit 1
                """).param("u", userId).query(UUID.class).optional();
    }

    public UUID create(String name, UUID userId) {
        UUID id = jdbc.sql("insert into households(name, created_by) values (:n, :u) returning id")
                .param("n", name).param("u", userId).query(UUID.class).single();
        jdbc.sql("insert into household_members(household_id, user_id, role) values (:h, :u, 'owner')")
                .param("h", id).param("u", userId).update();
        return id;
    }

    public int offsetDays(UUID householdId) {
        return jdbc.sql("select time_offset_days from households where id = :h")
                .param("h", householdId).query(Integer.class).single();
    }

    public void setOffsetDays(UUID householdId, int days) {
        jdbc.sql("update households set time_offset_days = :d, updated_at = now() where id = :h")
                .param("d", days).param("h", householdId).update();
    }

    /** Полная очистка данных домохозяйства (кнопка «Сбросить данные»). */
    public void wipe(UUID h) {
        String[] statements = {
                "delete from events where household_id = :h",
                "delete from purchases where household_id = :h",
                "delete from list_items where list_id in (select id from shopping_lists where household_id = :h)",
                "delete from stock_movements where household_id = :h",
                "delete from stock_items where household_id = :h",
                "delete from products where household_id = :h",
                "update households set time_offset_days = 0, updated_at = now() where id = :h"
        };
        for (String sql : statements) {
            jdbc.sql(sql).param("h", h).update();
        }
    }
}
