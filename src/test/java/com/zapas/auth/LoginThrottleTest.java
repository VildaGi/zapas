package com.zapas.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zapas.domain.TooManyRequestsException;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    @Test
    void blocksAfterLimitAndIsPerKey() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 3; i++) {
            t.check("a", 3);
            t.record("a");
        }
        assertThrows(TooManyRequestsException.class, () -> t.check("a", 3));
        assertDoesNotThrow(() -> t.check("b", 3));
    }

    @Test
    void resetClearsCounter() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 3; i++) t.record("a");
        t.reset("a");
        assertDoesNotThrow(() -> t.check("a", 3));
    }
}
