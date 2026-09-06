package com.orbit.auth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record for authentication-sensitive actions
 * (Blueprint §35 "login audit events", PRD §11).
 */
@Entity
@Table(name = "auth_events")
public class AuthEvent {

    public enum Type {
        REGISTERED,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        RATE_LIMITED
    }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private Type eventType;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Column(name = "detail", length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthEvent() {
        // JPA
    }

    AuthEvent(Type eventType, UUID userId, String email, String ip, String userAgent, String detail) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.userId = userId;
        this.email = email;
        this.ip = ip;
        this.userAgent = truncate(userAgent, 400);
        this.detail = truncate(detail, 200);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public Type getEventType() {
        return eventType;
    }

    public String getEmail() {
        return email;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
