package com.orbit.platform.ai.web;

import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.service.AiService;
import com.orbit.platform.ai.service.ConversationService;
import com.orbit.platform.ai.service.NudgeService;
import com.orbit.platform.ai.web.dto.OscarDtos.ConversationDetail;
import com.orbit.platform.ai.web.dto.OscarDtos.ConversationSummary;
import com.orbit.platform.ai.web.dto.OscarDtos.CreateConversationRequest;
import com.orbit.platform.ai.web.dto.OscarDtos.Nudge;
import com.orbit.platform.ai.web.dto.OscarDtos.OscarStatus;
import com.orbit.platform.ai.web.dto.OscarDtos.RenameConversationRequest;
import com.orbit.platform.ai.web.dto.OscarDtos.TurnRequest;
import com.orbit.platform.ai.web.dto.OscarDtos.TurnResponse;
import com.orbit.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/oscar} (Blueprint §20, ADR-032 §1). Conversations are the
 * caller's own — a foreign id is a 404. OSCAR never mutates ORBIT state here:
 * {@code proposedActions} on a turn response are confirmed by the frontend
 * calling the existing domain endpoints.
 */
@RestController
@RequestMapping("/api/v1/oscar")
public class OscarController {

    private final ConversationService conversations;
    private final NudgeService nudges;
    private final AiService ai;
    private final AiProperties props;

    public OscarController(ConversationService conversations, NudgeService nudges, AiService ai,
                           AiProperties props) {
        this.conversations = conversations;
        this.nudges = nudges;
        this.ai = ai;
        this.props = props;
    }

    /** Whether OSCAR is usable at all — the frontend shows the unavailable panel when {@code false}. */
    @GetMapping("/status")
    public ApiResponse<OscarStatus> status() {
        return ApiResponse.of(new OscarStatus(ai.isEnabled(), props.primary(), props.fallback(),
                props.dailyRequestCap()));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationSummary>> list() {
        return ApiResponse.of(conversations.listMine());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TurnResponse> create(@Valid @RequestBody(required = false) CreateConversationRequest req) {
        String title = req == null ? null : req.title();
        String message = req == null ? null : req.message();
        return ApiResponse.of(conversations.create(title, message));
    }

    @GetMapping("/conversations/{id}")
    public ApiResponse<ConversationDetail> get(@PathVariable UUID id) {
        return ApiResponse.of(conversations.getMine(id));
    }

    @PatchMapping("/conversations/{id}")
    public ApiResponse<ConversationSummary> rename(@PathVariable UUID id,
                                                   @Valid @RequestBody RenameConversationRequest req) {
        return ApiResponse.of(conversations.rename(id, req.title()));
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        conversations.delete(id);
    }

    @PostMapping("/conversations/{id}/turns")
    public ApiResponse<TurnResponse> turn(@PathVariable UUID id, @Valid @RequestBody TurnRequest req) {
        return ApiResponse.of(conversations.turn(id, req.message()));
    }

    /**
     * Streamed turn (ADR-033) — {@code text/event-stream}. Ownership / rate-limit
     * / validation happen synchronously (a 404 / 429 / 422 is a normal JSON
     * response); then SSE frames: {@code status} / {@code token} /
     * {@code proposedAction} / {@code meta} / {@code done} / {@code error}.
     */
    @PostMapping(value = "/conversations/{id}/turns/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter turnStream(@PathVariable UUID id, @Valid @RequestBody TurnRequest req) {
        return conversations.streamTurn(id, req.message());
    }

    /**
     * A contextual recommendation card for a page (ADR-034). Cached, single-shot,
     * no tools. {@code status} is {@code ready} / {@code none} / {@code unavailable}
     * — the client renders nothing for the latter two.
     */
    @GetMapping("/nudge")
    public ApiResponse<Nudge> nudge(@RequestParam String surface,
                                    @RequestParam(required = false) UUID objectiveId) {
        return ApiResponse.of(nudges.nudgeFor(surface, objectiveId));
    }
}
