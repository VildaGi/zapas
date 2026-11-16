package com.zapas.auth;

import com.zapas.domain.TooManyRequestsException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Простой ограничитель попыток в памяти процесса: не больше N событий на ключ за окно.
 * Для одного инстанса приложения этого достаточно; при нескольких инстансах лимит
 * считается на каждом отдельно.
 */
@Component
public class LoginThrottle {

    private static final long WINDOW_MS = Duration.ofMinutes(15).toMillis();
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> events = new ConcurrentHashMap<>();

    /** Бросает TooManyRequestsException, если за окно уже накопилось max событий. */
    public void check(String key, int max) {
        Deque<Long> d = events.get(key);
        if (d == null) return;
        synchronized (d) {
            prune(d, System.currentTimeMillis());
            if (d.size() >= max) {
                throw new TooManyRequestsException("Слишком много попыток. Попробуйте через 15 минут");
            }
        }
    }

    public void record(String key) {
        if (events.size() > CLEANUP_THRESHOLD) cleanup();
        Deque<Long> d = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (d) {
            prune(d, now);
            d.addLast(now);
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    private static void prune(Deque<Long> d, long now) {
        while (!d.isEmpty() && now - d.peekFirst() > WINDOW_MS) d.pollFirst();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }
}
