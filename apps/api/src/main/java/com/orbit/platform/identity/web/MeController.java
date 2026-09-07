package com.orbit.platform.identity.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.identity.model.User;
import com.orbit.platform.identity.model.UserProfile;
import com.orbit.platform.identity.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/me} and {@code PUT /api/v1/me/preferences} (Blueprint §20
 * Identity). {@code GET /me} is also where the user's local profile row is
 * provisioned just-in-time on first authenticated call (ADR-012 §3).
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
        return ApiResponse.of(build(currentUser.getOrProvision(), currentUser.getProfile()));
    }

    @PutMapping("/me/preferences")
    @Transactional
    public ApiResponse<MeResponse> updatePreferences(@Valid @RequestBody PreferencesRequest request) {
        UserProfile profile = currentUser.getProfile();
        if (request.timezone() != null && !request.timezone().isBlank()) {
            profile.setTimezone(request.timezone().strip());
        }
        if (request.availableDailyMinutes() != null) {
            profile.setAvailableDailyMinutes(request.availableDailyMinutes());
        }
        return ApiResponse.of(build(currentUser.getOrProvision(), profile));
    }

    private static MeResponse build(User user, UserProfile profile) {
        return new MeResponse(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus(),
                Boolean.TRUE.equals(user.getOnboardingCompleted()),
                profile.getTimezone(), profile.getAvailableDailyMinutes(),
                user.getCreatedAt());
    }

    public record PreferencesRequest(
            @Size(max = 64, message = "timezone is too long.") String timezone,
            @Positive(message = "availableDailyMinutes must be positive.") Integer availableDailyMinutes) {
    }

    public record MeResponse(
            UUID id,
            String email,
            String displayName,
            String status,
            boolean onboardingCompleted,
            String timezone,
            Integer availableDailyMinutes,
            Instant createdAt) {
    }
}
