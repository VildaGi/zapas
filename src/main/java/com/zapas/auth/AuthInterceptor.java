package com.zapas.auth;

import com.zapas.context.AuthUser;
import com.zapas.context.CurrentContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Пускает к /api только с действующей сессией и кладёт пользователя в CurrentContext. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService auth;
    private final CurrentContext ctx;

    public AuthInterceptor(AuthService auth, CurrentContext ctx) {
        this.auth = auth;
        this.ctx = ctx;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Личные данные не должны оседать в кэше браузера или прокси.
        response.setHeader("Cache-Control", "no-store");
        if ("OPTIONS".equals(request.getMethod())) return true;   // CORS preflight

        Optional<AuthUser> user = auth.authenticate(SessionCookie.read(request));
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Требуется вход\"}");
            return false;
        }
        ctx.set(user.get());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        ctx.clear();
    }
}
