package com.orbit.platform.ai.service;

import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.repository.AiMessageRepository;
import com.orbit.platform.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The guard that runs before a provider call and before each tool dispatch
 * (Blueprint §26/§37, ADR-032 §5):
 *
 * <ul>
 *   <li>per-user <b>daily request cap</b> (rolling 24h) — a heavy day can leave
 *       OSCAR rate-limited for a user well before the free-tier quota is hit;</li>
 *   <li><b>sanitises the user's message</b> — strips {@code <orbit_context>}
 *       markers and instruction-like control tokens so a user can't smuggle fake
 *       context or system instructions into the prompt;</li>
 *   <li>exposes the <b>per-turn token budgets</b> the context builder and the
 *       provider request honour.</li>
 * </ul>
 *
 * The tool allow-list and per-tool arg validation live in {@link ToolRegistry};
 * the conversation-ownership check lives in {@link ConversationService} (the M1
 * 404 pattern).
 */
@Component
public class PolicyGuard {

    private static final Logger log = LoggerFactory.getLogger(PolicyGuard.class);
    private static final int MAX_USER_MESSAGE_CHARS = 4000;

    /** {@code <orbit_context>} / {@code </orbit_context>} in any case, plus chat-template control tokens. */
    private static final Pattern INJECTION_MARKERS = Pattern.compile(
            "(?i)</?orbit_context>|<\\|[a-z_]+\\|>|<\\|(?:im_start|im_end|system|user|assistant)\\|>",
            Pattern.CASE_INSENSITIVE);

    private final AiProperties props;
    private final AiMessageRepository messages;
    private final Clock clock;

    public PolicyGuard(AiProperties props, AiMessageRepository messages, Clock clock) {
        this.props = props;
        this.messages = messages;
        this.clock = clock;
    }

    /** Throw a 429 if this user has already taken {@code dailyRequestCap} turns in the last 24h. */
    public void checkDailyCap(UUID userId) {
        long used = messages.countUserTurnsSince(userId, clock.instant().minus(Duration.ofHours(24)));
        if (used >= props.dailyRequestCap()) {
            log.info("event=oscar.policy.rate_limited user_id={} used={} cap={}",
                    userId, used, props.dailyRequestCap());
            throw ApiException.tooManyRequests(
                    "You've reached today's OSCAR limit (" + props.dailyRequestCap()
                            + " questions / 24h). Your tasks, objectives and analytics are unaffected.");
        }
    }

    /** Remove context/instruction markers, collapse control chars, and cap the length. */
    public String sanitizeUserMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = INJECTION_MARKERS.matcher(raw).replaceAll(" ");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").strip();
        if (cleaned.length() > MAX_USER_MESSAGE_CHARS) {
            cleaned = cleaned.substring(0, MAX_USER_MESSAGE_CHARS).strip();
        }
        return cleaned;
    }

    public int promptTokenBudget() {
        return props.promptTokenBudget();
    }

    public int outputTokenBudget() {
        return props.outputTokenBudget();
    }

    public int maxToolRounds() {
        return props.maxToolRounds();
    }
}
