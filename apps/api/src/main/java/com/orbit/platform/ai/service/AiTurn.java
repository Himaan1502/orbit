package com.orbit.platform.ai.service;

import com.orbit.platform.ai.provider.ChatItem;

import java.util.List;
import java.util.UUID;

/**
 * Everything {@link AiService} needs for one OSCAR exchange. Assembled by
 * {@link ConversationService}: the caller, the prior turns (as provider-neutral
 * {@link ChatItem}s), and the current — already {@link PolicyGuard}-sanitised —
 * user message.
 */
public record AiTurn(UUID userId, UUID conversationId, List<ChatItem> priorTurns, String userMessage) {
}
