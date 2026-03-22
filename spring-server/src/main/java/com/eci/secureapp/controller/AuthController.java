package com.eci.secureapp.controller;

import com.eci.secureapp.model.LoginRequest;
import com.eci.secureapp.service.TokenService;
import com.eci.secureapp.service.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication controller.
 * POST /api/auth/login  - authenticate and receive a token
 * POST /api/auth/register - register a new user
 * GET  /health          - health check
 */
@RestController
@RequestMapping
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "login-service");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword())
            );

            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_USER");

            String token = tokenService.generateToken(request.getUsername(), role);

            response.put("success", true);
            response.put("token", token);
            response.put("username", request.getUsername());
            response.put("role", role);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            response.put("success", false);
            response.put("error", "Invalid username or password");
            return ResponseEntity.status(401).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Authentication failed");
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (request.getUsername() == null || request.getUsername().length() < 3) {
            response.put("success", false);
            response.put("error", "Username must be at least 3 characters");
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            response.put("success", false);
            response.put("error", "Password must be at least 6 characters");
            return ResponseEntity.badRequest().body(response);
        }

        boolean created = userDetailsService.registerUser(
                request.getUsername(), request.getPassword(), "ROLE_USER");

        if (!created) {
            response.put("success", false);
            response.put("error", "Username already exists");
            return ResponseEntity.status(409).body(response);
        }

        response.put("success", true);
        response.put("message", "User registered successfully");
        return ResponseEntity.ok(response);
    }
}
