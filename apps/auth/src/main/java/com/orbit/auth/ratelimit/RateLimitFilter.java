package com.orbit.auth.ratelimit;

import com.orbit.auth.audit.AuthEvent;
import com.orbit.auth.audit.AuthEventService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-IP rate limit on the credential-accepting form POSTs
 * ({@code POST /login}, {@code POST /register}). In-memory (Bucket4j),
 * single-instance — see ADR-012 §5.
 */
@Component
@Order(-50)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int loginPerMinute;
    private final int registerPerMinute;
    private final AuthEventService auditEvents;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(Environment env, AuthEventService auditEvents) {
        this.loginPerMinute = Integer.parseInt(env.getProperty("orbit.auth.rate-limit.login-per-minute", "10"));
        this.registerPerMinute = Integer.parseInt(env.getProperty("orbit.auth.rate-limit.register-per-minute", "5"));
        this.auditEvents = auditEvents;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !("/login".equals(path) || "/register".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        int limit = "/register".equals(path) ? registerPerMinute : loginPerMinute;
        String ip = AuthEventService.clientIp(request);
        Bucket bucket = buckets.computeIfAbsent(path + "|" + ip, k -> newBucket(limit));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        auditEvents.record(AuthEvent.Type.RATE_LIMITED, request.getParameter("username"), request, "path=" + path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Too many attempts. Please wait a minute and try again.");
    }

    private static Bucket newBucket(int perMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
