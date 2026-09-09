package com.orbit.platform.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn in an {@link AiConversation} (Blueprint §18, ADR-032 §1).
 * <b>Append-only</b> — there is no mutator and no update path; a conversation
 * delete cascades. Only the user's message text and OSCAR's <em>final</em>
 * assistant text are ever stored here — never the assembled context bundle,
 * intermediate tool results, or raw provider payloads.
 */
@Entity
@Table(name = "ai_messages", schema = "orbit")
@Getter
@NoArgsConstructor
public class AiMessage {

    public enum Role { user, assistant }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "role", nullable = false, length = 16, updatable = false)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "text", updatable = false)
    private String content;

    @Column(name = "model_provider", length = 24, updatable = false)
    private String modelProvider;

    @Column(name = "model_name", length = 60, updatable = false)
    private String modelName;

    @Column(name = "prompt_version", length = 24, updatable = false)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage", columnDefinition = "jsonb", updatable = false)
    private TokenUsage tokenUsage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AiMessage user(UUID conversationId, String content, Instant now) {
        AiMessage m = new AiMessage();
        m.id = UUID.randomUUID();
        m.conversationId = conversationId;
        m.role = Role.user.name();
        m.content = content;
        m.createdAt = now;
        return m;
    }

    public static AiMessage assistant(UUID conversationId, String content, String provider,
                                      String modelName, String promptVersion, TokenUsage usage,
                                      Instant now) {
        AiMessage m = new AiMessage();
        m.id = UUID.randomUUID();
        m.conversationId = conversationId;
        m.role = Role.assistant.name();
        m.content = content;
        m.modelProvider = provider;
        m.modelName = modelName;
        m.promptVersion = promptVersion;
        m.tokenUsage = usage;
        m.createdAt = now;
        return m;
    }

    public Role roleEnum() {
        return Role.valueOf(role);
    }
}
