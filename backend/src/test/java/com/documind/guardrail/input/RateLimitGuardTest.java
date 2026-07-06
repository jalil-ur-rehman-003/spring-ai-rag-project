package com.documind.guardrail.input;

import com.documind.guardrail.domain.GuardrailViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitGuardTest {

    private static final int REQUESTS_PER_MINUTE = 3;

    private final RateLimitGuard rateLimitGuard = new RateLimitGuard(REQUESTS_PER_MINUTE);

    @Test
    void allowsRequestsUpToTheConfiguredLimit() {
        UUID userId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> {
            for (int i = 0; i < REQUESTS_PER_MINUTE; i++) {
                rateLimitGuard.checkAndConsume(userId);
            }
        });
    }

    @Test
    void rejectsTheRequestThatExceedsTheLimit() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < REQUESTS_PER_MINUTE; i++) {
            rateLimitGuard.checkAndConsume(userId);
        }

        assertThatThrownBy(() -> rateLimitGuard.checkAndConsume(userId))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void tracksLimitsIndependentlyPerUser() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        for (int i = 0; i < REQUESTS_PER_MINUTE; i++) {
            rateLimitGuard.checkAndConsume(firstUserId);
        }

        assertThatNoException().isThrownBy(() -> rateLimitGuard.checkAndConsume(secondUserId));
    }
}
