package com.orbit.auth.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Rejects {@code POST /login} while an email is in temporary lockout after too
 * many consecutive failures (ADR-012 §5). Redirects back to the login page with
 * {@code ?error&locked} so the form shows a clear message.
 */
@Component
@Order(-40)
public class LoginLockoutFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttempts;

    public LoginLockoutFilter(LoginAttemptService loginAttempts) {
        this.loginAttempts = loginAttempts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && "/login".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String email = request.getParameter("username");
        if (email != null && loginAttempts.isLocked(email.trim().toLowerCase(Locale.ROOT))) {
            response.sendRedirect(request.getContextPath() + "/login?error&locked");
            return;
        }
        chain.doFilter(request, response);
    }
}
