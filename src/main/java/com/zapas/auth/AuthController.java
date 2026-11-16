package com.zapas.auth;

import com.zapas.config.AppProperties;
import com.zapas.context.CurrentContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String email, String password) {}

    public record Me(UUID id, String email) {}

    private final AuthService auth;
    private final AppProperties props;
    private final CurrentContext ctx;

    public AuthController(AuthService auth, AppProperties props, CurrentContext ctx) {
        this.auth = auth;
        this.props = props;
        this.ctx = ctx;
    }

    @PostMapping("/register")
    public ResponseEntity<Me> register(@RequestBody Credentials body, HttpServletRequest req) {
        return withSession(auth.register(body.email(), body.password(), req.getRemoteAddr()), req);
    }

    @PostMapping("/login")
    public ResponseEntity<Me> login(@RequestBody Credentials body, HttpServletRequest req) {
        return withSession(auth.login(body.email(), body.password(), req.getRemoteAddr()), req);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        auth.logout(SessionCookie.read(req));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.clear(req))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /** Кто я (требует входа — проверяет AuthInterceptor). */
    @GetMapping("/me")
    public Me me() {
        var u = ctx.user();
        return new Me(u.userId(), u.email());
    }

    private ResponseEntity<Me> withSession(AuthService.Session s, HttpServletRequest req) {
        long maxAge = Duration.ofDays(props.sessionDaysOrDefault()).toSeconds();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.set(s.token(), maxAge, req))
                .cacheControl(CacheControl.noStore())
                .body(new Me(s.userId(), s.email()));
    }
}
