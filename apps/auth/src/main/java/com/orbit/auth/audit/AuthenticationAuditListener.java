package com.orbit.auth.audit;

import com.orbit.auth.identity.AuthUserRepository;
import com.orbit.auth.ratelimit.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.UUID;

/**
 * Turns Spring Security authentication events into {@link AuthEvent} audit
 * records and feeds the per-email failure throttle (ADR-012 §5).
 */
@Component
public class AuthenticationAuditListener {

    private final AuthEventService auditEvents;
    private final LoginAttemptService loginAttempts;
    private final AuthUserRepository users;

    public AuthenticationAuditListener(AuthEventService auditEvents,
                                       LoginAttemptService loginAttempts,
                                       AuthUserRepository users) {
        this.auditEvents = auditEvents;
        this.loginAttempts = loginAttempts;
        this.users = users;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        if (!isFormLogin(auth)) {
            return;
        }
        String email = auth.getName().toLowerCase(Locale.ROOT);
        loginAttempts.recordSuccess(email);
        UUID userId = users.findByEmail(email).map(u -> u.getId()).orElse(null);
        auditEvents.record(AuthEvent.Type.LOGIN_SUCCESS, userId, email, currentRequest());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        if (!isFormLogin(auth)) {
            return;
        }
        String email = String.valueOf(auth.getName()).toLowerCase(Locale.ROOT);
        loginAttempts.recordFailure(email);
        auditEvents.record(AuthEvent.Type.LOGIN_FAILURE, email, currentRequest(),
                event.getException().getClass().getSimpleName());
    }

    private static boolean isFormLogin(Authentication auth) {
        return auth != null
                && "UsernamePasswordAuthenticationToken".equals(auth.getClass().getSimpleName());
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
