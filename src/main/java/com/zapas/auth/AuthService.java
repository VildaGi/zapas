package com.zapas.auth;

import com.zapas.config.AppProperties;
import com.zapas.context.AuthUser;
import com.zapas.domain.ConflictException;
import com.zapas.domain.UnauthorizedException;
import com.zapas.repo.UserRepository;
import com.zapas.service.HouseholdService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Токен (отдаётся клиенту в cookie) и данные пользователя. */
    public record Session(String token, UUID userId, String email) {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 200;   // ограничиваем нагрузку на хеширование
    private static final int LOGIN_MAX_PER_ACCOUNT = 8;
    private static final int LOGIN_MAX_PER_IP = 30;
    private static final int REGISTER_MAX_PER_IP = 20;

    private final UserRepository users;
    private final HouseholdService households;
    private final AppProperties props;
    private final LoginThrottle throttle;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    /** Для несуществующего email всё равно считаем bcrypt, чтобы время ответа не выдавало, есть ли аккаунт. */
    private final String dummyHash = encoder.encode(prehash("zapas-dummy-password"));
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, HouseholdService households, AppProperties props,
                       LoginThrottle throttle) {
        this.users = users;
        this.households = households;
        this.props = props;
        this.throttle = throttle;
    }

    @Transactional
    public Session register(String rawEmail, String password, String ip) {
        String ipKey = "reg:" + ip;
        throttle.check(ipKey, REGISTER_MAX_PER_IP);
        throttle.record(ipKey);

        String email = normalizeEmail(rawEmail);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) {
            throw new IllegalArgumentException("Введите корректный email");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Пароль должен быть не короче " + MIN_PASSWORD + " символов");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new IllegalArgumentException("Пароль слишком длинный");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new ConflictException("Этот email уже зарегистрирован");
        }

        UUID id;
        try {
            id = users.insertUser(email, encoder.encode(prehash(password)));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Этот email уже зарегистрирован");
        }
        households.ensureFor(id);   // профиль + «Мой дом»
        return openSession(id, email);
    }

    public Session login(String rawEmail, String password, String ip) {
        String email = normalizeEmail(rawEmail);
        String accountKey = "acc:" + ip + "|" + email;
        String ipKey = "ip:" + ip;
        throttle.check(accountKey, LOGIN_MAX_PER_ACCOUNT);
        throttle.check(ipKey, LOGIN_MAX_PER_IP);

        Optional<UserRepository.UserRow> user = users.findByEmail(email);
        String hash = user.map(UserRepository.UserRow::passwordHash).orElse(dummyHash);
        boolean passwordOk = password != null && password.length() <= MAX_PASSWORD
                && encoder.matches(prehash(password), hash);

        if (user.isEmpty() || !passwordOk) {
            throttle.record(accountKey);
            throttle.record(ipKey);
            throw new UnauthorizedException("Неверный email или пароль");
        }
        throttle.reset(accountKey);
        return openSession(user.get().id(), user.get().email());
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) users.deleteSession(sha256(token));
    }

    /** По токену из cookie возвращает пользователя и его домохозяйство. */
    public Optional<AuthUser> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return users.findSessionUser(sha256(token))
                .map(u -> new AuthUser(u.id(), households.householdOf(u.id()), u.email()));
    }

    private Session openSession(UUID userId, String email) {
        users.deleteExpiredSessions();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = Instant.now().plus(props.sessionDaysOrDefault(), ChronoUnit.DAYS);
        users.insertSession(userId, sha256(token), expires);
        return new Session(token, userId, email);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * bcrypt учитывает только первые 72 байта, а кириллица занимает по 2 байта. Поэтому пароль
     * сначала сворачиваем SHA-256 в 44 ASCII-символа: длина и алфавит пароля не ограничены.
     */
    private static String prehash(String password) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
