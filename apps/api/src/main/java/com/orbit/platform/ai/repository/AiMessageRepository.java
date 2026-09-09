package com.orbit.platform.ai.repository;

import com.orbit.platform.ai.model.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** User turns this user has taken since {@code since}, across all their conversations — the PolicyGuard daily cap. */
    @Query("""
            select count(m) from AiMessage m
            where m.role = 'user' and m.createdAt >= :since
              and m.conversationId in (select c.id from AiConversation c where c.userId = :userId)
            """)
    long countUserTurnsSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
