package com.orbit.platform.ai.repository;

import com.orbit.platform.ai.model.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    /** Ownership-scoped lookup — the M1 pattern; a miss becomes a 404. */
    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
