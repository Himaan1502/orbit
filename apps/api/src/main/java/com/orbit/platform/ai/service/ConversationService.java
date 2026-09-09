package com.orbit.platform.ai.service;

import com.orbit.platform.ai.model.AiConversation;
import com.orbit.platform.ai.model.AiMessage;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.repository.AiConversationRepository;
import com.orbit.platform.ai.repository.AiMessageRepository;
import com.orbit.platform.ai.web.dto.OscarDtos.ConversationDetail;
import com.orbit.platform.ai.web.dto.OscarDtos.ConversationSummary;
import com.orbit.platform.ai.web.dto.OscarDtos.MessageView;
import com.orbit.platform.ai.web.dto.OscarDtos.TurnResponse;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the {@code ai_conversations} / {@code ai_messages} lifecycle and drives a
 * turn through {@link AiService} (ADR-032 §1). Every path is ownership-scoped —
 * a conversation that isn't the caller's returns 404 (the M1 pattern). Only the
 * user message + OSCAR's final text are persisted, and only when the turn is
 * {@code persistable} (not on a degraded / unparsable response, ADR-032 §1/§6).
 *
 * <p>The turn itself (context assembly, provider HTTP, tool dispatch) runs with
 * <b>no DB transaction</b> — a domain service that throws while building context
 * (e.g. "no career target set") would otherwise mark a surrounding transaction
 * rollback-only and the message write would fail. The row creation and the
 * message persist are each their own small transaction via {@link #tx}.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final int MAX_HISTORY_TURNS = 12;

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final AiService ai;
    private final PolicyGuard policyGuard;
    private final CurrentUserService currentUser;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ConversationService(AiConversationRepository conversations, AiMessageRepository messages,
                               AiService ai, PolicyGuard policyGuard, CurrentUserService currentUser,
                               Clock clock, PlatformTransactionManager txManager) {
        this.conversations = conversations;
        this.messages = messages;
        this.ai = ai;
        this.policyGuard = policyGuard;
        this.currentUser = currentUser;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listMine() {
        return conversations.findByUserIdOrderByUpdatedAtDesc(currentUser.currentUserId()).stream()
                .map(ConversationSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail getMine(UUID id) {
        AiConversation c = requireOwned(id);
        List<MessageView> views = messages.findByConversationIdOrderByCreatedAtAsc(c.getId()).stream()
                .map(MessageView::of)
                .toList();
        return new ConversationDetail(ConversationSummary.of(c), views);
    }

    @Transactional
    public ConversationSummary rename(UUID id, String title) {
        AiConversation c = requireOwned(id);
        c.rename(title, clock.instant());
        return ConversationSummary.of(conversations.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        conversations.delete(requireOwned(id));   // ai_messages cascade (FK ON DELETE CASCADE)
    }

    /** Create a conversation; if {@code openingMessage} is present, run the first turn. */
    public TurnResponse create(String title, String openingMessage) {
        UUID userId = currentUser.getOrProvision().getId();
        AiConversation created = tx.execute(s -> conversations.save(AiConversation.start(userId,
                title != null && !title.isBlank() ? title : openingMessage, clock.instant())));
        if (openingMessage == null || openingMessage.isBlank()) {
            return new TurnResponse(ConversationSummary.of(created), null, List.of());
        }
        return runTurn(created, openingMessage);
    }

    public TurnResponse turn(UUID conversationId, String message) {
        return runTurn(tx.execute(s -> requireOwned(conversationId)), message);
    }

    // ---------------------------------------------------------------------

    private TurnResponse runTurn(AiConversation c, String rawMessage) {
        UUID userId = c.getUserId();
        tx.executeWithoutResult(s -> policyGuard.checkDailyCap(userId));

        String sanitized = policyGuard.sanitizeUserMessage(rawMessage);
        if (sanitized.isBlank()) {
            throw ApiException.badRequest("message is empty after sanitisation.");
        }

        List<ChatItem> prior = tx.execute(s -> priorTurns(c.getId()));
        TurnResult result = ai.respond(new AiTurn(userId, c.getId(), prior, sanitized));

        if (!result.persistable()) {
            log.info("event=oscar.turn user_id={} conversation_id={} outcome={} persisted=false",
                    userId, c.getId(),
                    result.response().meta() != null ? result.response().meta().outcome() : "degraded");
            return new TurnResponse(ConversationSummary.of(c), result.response(), List.of());
        }

        Persisted p = tx.execute(s -> persist(c.getId(), sanitized, result));
        log.info("event=oscar.turn user_id={} conversation_id={} provider={} model={} prompt_version={} "
                        + "prompt_tokens={} completion_tokens={} outcome=ok",
                userId, c.getId(), result.provider(), result.model(), result.promptVersion(),
                result.usage().promptTokens(), result.usage().completionTokens());
        return new TurnResponse(ConversationSummary.of(p.conversation()), result.response(), p.messages());
    }

    private record Persisted(AiConversation conversation, List<MessageView> messages) {
    }

    private Persisted persist(UUID conversationId, String userText, TurnResult result) {
        Instant now = clock.instant();
        AiConversation c = conversations.findById(conversationId).orElseThrow();
        AiMessage userMsg = messages.save(AiMessage.user(conversationId, userText, now));
        AiMessage assistantMsg = messages.save(AiMessage.assistant(conversationId,
                result.assistantText() == null ? result.response().message() : result.assistantText(),
                result.provider(), result.model(), result.promptVersion(), result.usage(), now));
        if (isDefaultTitle(c.getTitle())) {
            c.rename(userText, now);
        }
        c.touch(now);
        conversations.save(c);
        return new Persisted(c, List.of(MessageView.of(userMsg), MessageView.of(assistantMsg)));
    }

    private List<ChatItem> priorTurns(UUID conversationId) {
        List<AiMessage> all = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int from = Math.max(0, all.size() - MAX_HISTORY_TURNS);
        return all.subList(from, all.size()).stream()
                .map(m -> m.roleEnum() == AiMessage.Role.user
                        ? (ChatItem) new ChatItem.UserText(m.getContent())
                        : new ChatItem.ModelText(m.getContent()))
                .toList();
    }

    private AiConversation requireOwned(UUID id) {
        return conversations.findByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Conversation not found."));
    }

    private static boolean isDefaultTitle(String title) {
        return title == null || title.isBlank() || title.equals("New conversation");
    }
}
