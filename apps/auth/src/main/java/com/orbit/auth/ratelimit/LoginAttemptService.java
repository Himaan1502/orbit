package com.orbit.auth.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-email failed-login throttle and temporary lockout (ADR-012 §5,
 * Blueprint §35 "rate limiting" / account lockout).
 *
 * <p>Milestone 1: in-memory, single-instance. State resets on restart.
 * Redis-backed distributed state is deferred — see
 * {@code docs/engineering/deferred-items.md}.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration lockout;
    private final Map<String, Attempts> byEmail = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${orbit.auth.rate-limit.max-login-failures:10}") int maxFailures,
            @Value("${orbit.auth.rate-limit.lockout-minutes:15}") int lockoutMinutes) {
        this.maxFailures = maxFailures;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    public boolean isLocked(String email) {
        Attempts a = byEmail.get(key(email));
        return a != null && a.lockedUntil != null && Instant.now().isBefore(a.lockedUntil);
    }

    public void recordFailure(String email) {
        byEmail.compute(key(email), (k, existing) -> {
            Attempts a = existing == null ? new Attempts() : existing;
            a.count++;
            if (a.count >= maxFailures) {
                a.lockedUntil = Instant.now().plus(lockout);
                a.count = 0;
            }
            return a;
        });
    }

    public void recordSuccess(String email) {
        byEmail.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class Attempts {
        private int count;
        private Instant lockedUntil;
    }
}
