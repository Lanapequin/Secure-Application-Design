package com.workshop.secure_app.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Permite requests desde Apache (ajustar dominio en prod)
public class ApiController {

    // Endpoint público
    @GetMapping("/public/status")
    public Map<String, String> status() {
        return Map.of(
                "status", "online",
                "message", "Secure Spring API running with TLS"
        );
    }

    // Endpoint protegido (requiere login)
    @GetMapping("/secure/profile")
    public Map<String, String> profile(Authentication auth) {
        return Map.of(
                "username", auth.getName(),
                "message", "Authenticated successfully via TLS"
        );
    }
}