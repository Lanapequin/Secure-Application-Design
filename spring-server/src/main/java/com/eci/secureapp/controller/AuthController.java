package com.eci.secureapp.controller;

import com.eci.secureapp.model.AuthRequest;
import com.eci.secureapp.service.JwtService;
import com.eci.secureapp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public authentication endpoints.
 * POST /api/auth/login    — returns a JWT token
 * POST /api/auth/register — creates a new user
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthenticationManager authManager;
    @Autowired private JwtService            jwtService;
    @Autowired private UserService           userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        if (req.getUsername() == null || req.getPassword() == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required"));
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsername(), req.getPassword()));

            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst().orElse("ROLE_USER");

            String token = jwtService.generate(req.getUsername(), role);

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "token",    token,
                    "username", req.getUsername(),
                    "role",     role
            ));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest req) {
        if (req.getUsername() == null || req.getUsername().length() < 3)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username must be at least 3 characters"));

        if (req.getPassword() == null || req.getPassword().length() < 6)
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 6 characters"));

        boolean created = userService.register(
                req.getUsername(), req.getPassword(), "ROLE_USER");

        if (!created)
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Username already exists"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User registered successfully. You can now log in."
        ));
    }
}
