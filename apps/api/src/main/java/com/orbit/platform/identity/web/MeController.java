package com.orbit.platform.identity.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.identity.model.User;
import com.orbit.platform.identity.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/me} (Blueprint §20 Identity). Also the point where a user's
 * local profile row is provisioned just-in-time on first authenticated call
 * (ADR-012 §3) — the web app hits this immediately after login.
 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final CurrentUserService currentUser;

    public MeController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        User user = currentUser.getOrProvision();
        return ApiResponse.of(new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                Boolean.TRUE.equals(user.getOnboardingCompleted()),
                user.getCreatedAt()));
    }

    public record MeResponse(
            UUID id,
            String email,
            String displayName,
            String status,
            boolean onboardingCompleted,
            Instant createdAt) {
    }
}
