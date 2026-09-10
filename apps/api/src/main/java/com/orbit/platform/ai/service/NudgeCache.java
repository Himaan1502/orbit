package com.orbit.platform.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed store for contextual-card results + the per-user hourly
 * generation budget (ADR-034 §3/§4). <b>Every operation degrades to "nothing"
 * on a Redis error</b> — a cache miss with Redis down means no stale card and
 * the budget reads as exhausted, so nudges simply go silent (never an error,
 * never an uncached provider call).
 */
@Component
class NudgeCache {

    private static final Logger log = LoggerFactory.getLogger(NudgeCache.class);
    private static final Duration TTL = Duration.ofHours(12);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMdd'H'HH").withZone(ZoneOffset.UTC);

    private final StringRedisTemplate redis;

    NudgeCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (RuntimeException e) {
            log.debug("event=oscar.nudge.cache_unavailable op=get");
            return Optional.empty();
        }
    }

    void put(String key, String json) {
        try {
            redis.opsForValue().set(key, json, TTL);
        } catch (RuntimeException e) {
            log.debug("event=oscar.nudge.cache_unavailable op=put");
        }
    }

    /** {@code true} when this user has already generated {@code cap} cards this hour (or Redis is down). */
    boolean hourlyBudgetExhausted(UUID userId, int cap) {
        if (cap <= 0) {
            return true;
        }
        try {
            String v = redis.opsForValue().get(hourKey(userId));
            return v != null && Integer.parseInt(v) >= cap;
        } catch (RuntimeException e) {
            return true;
        }
    }

    void recordGeneration(UUID userId) {
        try {
            String key = hourKey(userId);
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, Duration.ofHours(1));
            }
        } catch (RuntimeException e) {
            log.debug("event=oscar.nudge.cache_unavailable op=incr");
        }
    }

    private static String hourKey(UUID userId) {
        return "oscar:nudge:count:" + userId + ":" + HOUR.format(Instant.now());
    }
}
