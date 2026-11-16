package com.zapas.repo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public record UserRow(UUID id, String email, String passwordHash) {}

    public record SessionUser(UUID id, String email) {}

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("select id, email, password_hash from app_users where email = :e")
                .param("e", email)
                .query((rs, i) -> new UserRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("password_hash")))
                .optional();
    }

    public UUID insertUser(String email, String passwordHash) {
        return jdbc.sql("insert into app_users(email, password_hash) values (:e, :h) returning id")
                .param("e", email).param("h", passwordHash)
                .query(UUID.class).single();
    }

    public void insertSession(UUID userId, String tokenHash, Instant expiresAt) {
        jdbc.sql("insert into auth_sessions(user_id, token_hash, expires_at) values (:u, :t, :x)")
                .param("u", userId).param("t", tokenHash).param("x", Db.ts(expiresAt))
                .update();
    }

    public Optional<SessionUser> findSessionUser(String tokenHash) {
        return jdbc.sql("""
                select u.id, u.email
                  from auth_sessions s
                  join app_users u on u.id = s.user_id
                 where s.token_hash = :t and s.expires_at > now()
                """)
                .param("t", tokenHash)
                .query((rs, i) -> new SessionUser(rs.getObject("id", UUID.class), rs.getString("email")))
                .optional();
    }

    public void deleteSession(String tokenHash) {
        jdbc.sql("delete from auth_sessions where token_hash = :t").param("t", tokenHash).update();
    }

    public void deleteExpiredSessions() {
        jdbc.sql("delete from auth_sessions where expires_at <= now()").update();
    }
}
