package com.orbit.platform.identity.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ORBIT domain profile for a user (Blueprint §7.1).
 *
 * <p>The {@code id} equals the OIDC {@code sub} claim, which equals
 * {@code auth.users.id} in the auth server. This table holds <strong>no
 * credentials</strong> — the API performs no password verification (ADR-004 §1).
 * Rows are provisioned just-in-time on first authenticated request
 * (ADR-012 §3, {@code CurrentUserService}).
 */
@Entity
@Table(name = "users", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "active";

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "onboarding_completed")
    @Builder.Default
    private Boolean onboardingCompleted = false;
}
