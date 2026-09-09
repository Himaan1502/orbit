package com.orbit.platform.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An OSCAR chat thread (Blueprint §18, ADR-032 §1). Owned by exactly one user;
 * {@code title} is the first user message trimmed and is user-editable.
 * {@code updatedAt} advances on every new turn so the list orders by recency.
 */
@Entity
@Table(name = "ai_conversations", schema = "orbit")
@Getter
@NoArgsConstructor
public class AiConversation {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AiConversation start(UUID userId, String title, Instant now) {
        AiConversation c = new AiConversation();
        c.id = UUID.randomUUID();
        c.userId = userId;
        c.title = trimTitle(title);
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    /** Move {@code updatedAt} — called when a turn adds messages. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void rename(String newTitle, Instant now) {
        this.title = trimTitle(newTitle);
        this.updatedAt = now;
    }

    private static String trimTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "New conversation";
        }
        String t = raw.strip();
        return t.length() <= 160 ? t : t.substring(0, 157) + "…";
    }
}
