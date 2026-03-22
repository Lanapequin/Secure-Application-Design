package com.eci.secureapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Protected REST API endpoints.
 * All routes here require a valid JWT (enforced by JwtFilter).
 *
 * GET /health         — public health check
 * GET /api/hello      — protected greeting
 * GET /api/data       — protected sample data
 * GET /api/whoami     — returns authenticated user info
 */
@RestController
public class ApiController {

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "ECI Secure App — Spring Server",
                "tls",     "enabled"
        ));
    }

    @GetMapping("/api/hello")
    public ResponseEntity<?> hello(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        return ResponseEntity.ok(Map.of(
                "message",   "Hello from the secure Spring server!",
                "user",      user,
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/api/data")
    public ResponseEntity<?> data(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        return ResponseEntity.ok(Map.of(
                "requestedBy", user,
                "records", List.of(
                        Map.of("id", 1, "course", "Arquitectura de Software", "grade", 4.5),
                        Map.of("id", 2, "course", "Seguridad Informática",    "grade", 4.8),
                        Map.of("id", 3, "course", "Redes de Computadores",    "grade", 4.2)
                ),
                "total", 3
        ));
    }

    @GetMapping("/api/whoami")
    public ResponseEntity<?> whoami(HttpServletRequest request) {
        String user = (String) request.getAttribute("username");
        return ResponseEntity.ok(Map.of(
                "username",      user,
                "authMechanism", "JWT / HMAC-SHA256",
                "passwordAlgo",  "BCrypt strength-10",
                "transport",     "HTTPS / TLS 1.2+",
                "server",        "Spring 5 + Jetty (embedded)"
        ));
    }
}
