package com.orbit.auth.config;

import com.orbit.auth.identity.AuthUser;
import com.orbit.auth.identity.AuthUserRepository;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shapes ORBIT's token claims (ADR-004 §1, ADR-012 §2):
 *
 * <ul>
 *   <li>{@code sub} → the user's UUID (stable, non-reassignable — proper OIDC
 *       {@code sub} semantics), not the email;</li>
 *   <li>{@code email} → the user's email;</li>
 *   <li>{@code roles} → e.g. {@code ["USER"]}, which {@code apps/api} maps to
 *       {@code ROLE_*}.</li>
 * </ul>
 *
 * <p>Applied to the access token and the OIDC ID token. The principal name is
 * the email; the UUID is looked up here (one indexed read per token issuance).
 */
@Component
public class OrbitTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final AuthUserRepository users;

    public OrbitTokenCustomizer(AuthUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        boolean isAccessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
        boolean isIdToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());
        if (!isAccessToken && !isIdToken) {
            return;
        }

        String principalName = context.getPrincipal().getName();
        if (principalName == null) {
            return;
        }
        AuthUser user = users.findByEmail(principalName.toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null) {
            return;
        }

        // A mutable ArrayList, not List.of(...): the Spring Authorization Server
        // JDBC store round-trips token metadata through a Jackson class allowlist
        // that accepts ArrayList but not the JDK immutable list types.
        List<String> roles = new ArrayList<>();
        roles.add("USER");

        context.getClaims().claims(claims -> {
            claims.put("sub", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("roles", roles);
            if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
                claims.put("name", user.getDisplayName());
            }
        });
    }
}
