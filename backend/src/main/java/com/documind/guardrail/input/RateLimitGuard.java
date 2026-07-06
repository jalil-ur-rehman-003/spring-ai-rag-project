package com.documind.guardrail.input;

import com.documind.guardrail.domain.GuardrailSeverity;
import com.documind.guardrail.domain.GuardrailType;
import com.documind.guardrail.domain.GuardrailViolationException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user token-bucket rate limiting on chat queries. In-memory only, one
 * bucket per user id -- correct for the current single-instance deployment;
 * would need a distributed store (e.g. Bucket4j's Redis integration) if the
 * app ever scales to multiple instances, same caveat as
 * DocumentProgressPublisher.
 */
@Component
public class RateLimitGuard {

    private final Map<UUID, Bucket> bucketsByUserId = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitGuard(@Value("${documind.guardrail.rate-limit.requests-per-minute}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public void checkAndConsume(UUID userId) {
        Bucket bucket = bucketsByUserId.computeIfAbsent(userId, id -> newBucket());

        if (!bucket.tryConsume(1)) {
            throw new GuardrailViolationException(
                    "Rate limit exceeded. Please wait before sending another message.",
                    GuardrailType.RATE_LIMIT_EXCEEDED,
                    GuardrailSeverity.LOW,
                    Map.of("userId", userId.toString(), "limitPerMinute", requestsPerMinute)
            );
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
