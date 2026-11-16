package com.zapas.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

/** Cookie сессии: HttpOnly (недоступна из JS), SameSite=Strict (защита от CSRF), Secure на HTTPS. */
final class SessionCookie {

    static final String NAME = "zapas_session";

    private SessionCookie() {}

    static String read(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    static String set(String token, long maxAgeSeconds, HttpServletRequest req) {
        return build(token, maxAgeSeconds, req);
    }

    static String clear(HttpServletRequest req) {
        return build("", 0, req);
    }

    private static String build(String value, long maxAgeSeconds, HttpServletRequest req) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(req.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
