package com.orbit.platform.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts a validated OIDC access token into a Spring Security authentication.
 *
 * <p>Authorities come from two places:
 * <ul>
 *   <li>OAuth 2.0 scopes → {@code SCOPE_*} (via {@link JwtGrantedAuthoritiesConverter});</li>
 *   <li>a configurable roles claim (default {@code roles}) → {@code ROLE_*}.</li>
 * </ul>
 * The principal name is the token subject ({@code sub}). Resource-ownership
 * checks are enforced separately in domain services — see ADR-004.
 */
@Component
public class OidcJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    private final String rolesClaim;
    private final String rolesPrefix;

    public OidcJwtAuthenticationConverter(
            @Value("${orbit.security.authorities-claim:roles}") String rolesClaim,
            @Value("${orbit.security.authorities-prefix:ROLE_}") String rolesPrefix) {
        this.rolesClaim = rolesClaim;
        this.rolesPrefix = rolesPrefix;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }

        for (String role : extractRoles(jwt)) {
            if (StringUtils.hasText(role)) {
                authorities.add(new SimpleGrantedAuthority(rolesPrefix + role));
            }
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private List<String> extractRoles(Jwt jwt) {
        Object claim = jwt.getClaim(rolesClaim);
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (claim instanceof String s && StringUtils.hasText(s)) {
            return List.of(s.trim().split("\\s+"));
        }
        return List.of();
    }
}
