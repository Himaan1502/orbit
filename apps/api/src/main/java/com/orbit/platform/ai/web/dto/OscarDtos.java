package com.orbit.platform.ai.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.orbit.platform.ai.model.AiConversation;
import com.orbit.platform.ai.model.AiMessage;
import com.orbit.platform.ai.service.OscarResponse;
import com.orbit.platform.ai.service.ProposedAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/oscar}. */
public final class OscarDtos {

    private OscarDtos() {
    }

    public record CreateConversationRequest(
            @Size(max = 160) String title,
            @Size(max = 4000) String message) {
    }

    public record RenameConversationRequest(
            @NotBlank @Size(max = 160) String title) {
    }

    public record TurnRequest(
            @NotBlank(message = "message is required.")
            @Size(max = 4000, message = "message is too long.")
            String message) {
    }

    public record OscarStatus(boolean enabled, String primary, String fallback, int dailyRequestCap) {
    }

    public record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt) {
        public static ConversationSummary of(AiConversation c) {
            return new ConversationSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(UUID id, String role, String content, String provider, String model,
                              Instant createdAt) {
        public static MessageView of(AiMessage m) {
            return new MessageView(m.getId(), m.getRole(), m.getContent(),
                    m.getModelProvider(), m.getModelName(), m.getCreatedAt());
        }
    }

    public record ConversationDetail(ConversationSummary conversation, List<MessageView> messages) {
    }

    /** Result of running a turn (or creating a conversation with an opening message). */
    public record TurnResponse(ConversationSummary conversation, OscarResponse response,
                               List<MessageView> newMessages) {
    }

    // ----- contextual cards (ADR-034) -----

    /** {@code ready} carries a card; {@code none} / {@code unavailable} render nothing. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Nudge(String status, NudgeCard card) {
        public static Nudge none() {
            return new Nudge("none", null);
        }

        public static Nudge unavailable() {
            return new Nudge("unavailable", null);
        }

        public static Nudge ready(NudgeCard card) {
            return new Nudge("ready", card);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NudgeCard(String headline, String body, String why,
                            ProposedAction proposedAction, Instant generatedAt) {
    }
}
