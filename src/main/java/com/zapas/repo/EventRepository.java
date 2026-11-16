package com.zapas.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zapas.web.Dto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public EventRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insert(UUID h, UUID actor, UUID stockItemId, UUID listItemId,
                       String type, Map<String, ?> props, Instant at) {
        String json;
        try {
            json = mapper.writeValueAsString(props == null ? Map.of() : props);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Некорректные properties", e);
        }
        jdbc.sql("""
                insert into events(household_id, actor_user_id, stock_item_id, list_item_id,
                                   event_type, occurred_at, properties)
                values (:h, :a, :s, :li, :t, :at, cast(:p as jsonb))
                """)
                .param("h", h).param("a", actor).param("s", stockItemId).param("li", listItemId)
                .param("t", type).param("at", Db.ts(at)).param("p", json)
                .update();
    }

    public int count(UUID h, String type) {
        return jdbc.sql("select count(*) from events where household_id = :h and event_type = :t")
                .param("h", h).param("t", type).query(Integer.class).single();
    }

    public List<Dto.Surprise> surprises(UUID h) {
        return jdbc.sql("""
                select coalesce(properties->>'name', '') as name, occurred_at
                  from events
                 where household_id = :h and event_type = 'ran_out_suddenly'
                 order by occurred_at
                """)
                .param("h", h)
                .query((rs, i) -> new Dto.Surprise(
                        rs.getString("name"),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant().toEpochMilli()))
                .list();
    }
}
