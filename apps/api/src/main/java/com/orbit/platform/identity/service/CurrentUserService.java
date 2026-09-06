package com.orbit.platform.identity.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.model.User;
import com.orbit.platform.identity.model.UserProfile;
import com.orbit.platform.identity.repository.UserProfileRepository;
import com.orbit.platform.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the authenticated caller to a local {@code orbit.users} row,
 * provisioning it just-in-time from the OIDC token on first contact
 * (ADR-012 §3). The API never talks to the auth server for this — everything
 * needed is in the validated token ({@code sub} = the user's UUID, {@code email},
 * optionally {@code name}).
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final UserRepository users;
    private final UserProfileRepository profiles;

    public CurrentUserService(UserRepository users, UserProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    /** The current user's id (the token {@code sub}). Cheap — no database access. */
    public UUID currentUserId() {
        return UUID.fromString(currentJwt().getSubject());
    }

    /** The current user's local profile row, creating it (and an empty preferences row) if absent. */
    @Transactional
    public User getOrProvision() {
        Jwt jwt = currentJwt();
        UUID id = UUID.fromString(jwt.getSubject());
        return users.findById(id).orElseGet(() -> provision(id, jwt));
    }

    private User provision(UUID id, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            // A token without an email claim cannot identify a usable account.
            throw ApiException.unauthorized("Token is missing the email claim.");
        }
        String name = jwt.getClaimAsString("name");

        User user = User.builder()
                .email(email.toLowerCase(java.util.Locale.ROOT))
                .displayName(name != null && !name.isBlank() ? name : null)
                .status("active")
                .onboardingCompleted(false)
                .build();
        user.setId(id);
        user = users.save(user);
        profiles.save(UserProfile.forUser(id));

        log.info("event=identity.user.provisioned user_id={} email={}", id, user.getEmail());
        return user;
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw ApiException.unauthorized("No authenticated user.");
    }
}
