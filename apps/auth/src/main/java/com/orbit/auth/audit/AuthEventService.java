package com.orbit.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records {@link AuthEvent}s to the database and to the structured log. Writes
 * run in their own transaction so an audit failure never rolls back the action
 * being audited, and vice versa.
 */
@Service
public class AuthEventService {

    private static final Logger log = LoggerFactory.getLogger("com.orbit.auth.audit");

    private final AuthEventRepository repository;

    public AuthEventService(AuthEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthEvent.Type type, UUID userId, String email,
                       String ip, String userAgent, String detail) {
        try {
            repository.save(new AuthEvent(type, userId, email, ip, userAgent, detail));
        } catch (RuntimeException ex) {
            log.warn("event=auth.audit.persist_failed type={} email={} error={}", type, email, ex.toString());
        }
        log.info("event=auth.{} email={} ip={} user_id={} detail={}",
                type.name().toLowerCase(), email, ip, userId, detail == null ? "" : detail);
    }

    public void record(AuthEvent.Type type, String email, HttpServletRequest request, String detail) {
        record(type, null, email, clientIp(request), header(request, "User-Agent"), detail);
    }

    public void record(AuthEvent.Type type, UUID userId, String email, HttpServletRequest request) {
        record(type, userId, email, clientIp(request), header(request, "User-Agent"), null);
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }
}
