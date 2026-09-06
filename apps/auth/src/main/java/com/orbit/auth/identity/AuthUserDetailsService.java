package com.orbit.auth.identity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Loads login credentials from {@code auth.users} (ADR-012). Replaces the
 * Milestone 0 {@code InMemoryUserDetailsManager}.
 *
 * <p>Returns a stock {@link User} rather than a custom {@code UserDetails}: the
 * Spring Authorization Server's JDBC store serialises the principal through a
 * class allowlist, and {@code org.springframework.security.core.userdetails.User}
 * is on it. The user's UUID (needed for the {@code sub} claim) is resolved from
 * the email by {@code OrbitTokenCustomizer} at token-issue time.
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final AuthUserRepository users;

    public AuthUserDetailsService(AuthUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String email = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        AuthUser user = users.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}
