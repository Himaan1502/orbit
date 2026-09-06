package com.orbit.auth.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Server-rendered signup. Password entry lives here on the auth server, never
 * in the SPA (UX §15.1 "no unnecessary credential collection"; ADR-013 §2).
 */
@Controller
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/signup")
    public String signupForm(@ModelAttribute("form") RegistrationForm form) {
        return "signup";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult binding,
                           HttpServletRequest request,
                           Model model) {
        if (!binding.hasErrors()) {
            try {
                registrationService.register(form, request);
                return "redirect:/login?registered";
            } catch (RegistrationException ex) {
                binding.rejectValue(ex.getField(), "registration.error", ex.getMessage());
            }
        }
        model.addAttribute("form", form);
        return "signup";
    }
}
