package com.orbit.auth.identity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom login page used by the Authorization Code flow's
 * {@code LoginUrlAuthenticationEntryPoint} redirect. The form POSTs to Spring
 * Security's {@code /login} filter.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
