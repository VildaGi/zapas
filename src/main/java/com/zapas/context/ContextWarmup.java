package com.zapas.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Fail-fast: при старте создаём профиль и домохозяйство, чтобы ошибки конфигурации были видны сразу. */
@Component
public class ContextWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContextWarmup.class);

    private final CurrentContext ctx;

    public ContextWarmup(CurrentContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Пользователь {} → домохозяйство {}", ctx.userId(), ctx.householdId());
    }
}
