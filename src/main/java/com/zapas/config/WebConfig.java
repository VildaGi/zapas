package com.zapas.config;

import com.zapas.auth.AuthInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final AuthInterceptor auth;

    public WebConfig(AppProperties props, AuthInterceptor auth) {
        this.props = props;
        this.auth = auth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Всё под /api требует входа, кроме регистрации, входа и выхода.
        registry.addInterceptor(auth)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/auth/logout");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = props.corsOrigins();
        if (origins.isEmpty()) return;
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }
}
