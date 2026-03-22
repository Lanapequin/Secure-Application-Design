package com.eci.secureapp.service;

import com.eci.secureapp.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * In-memory user store.
 * Passwords are stored as BCrypt hashes (12 factor: no plain-text secrets in code).
 * In production, replace with DB-backed UserDetailsService.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    // In-memory "database" of users
    private final Map<String, User> users = new HashMap<>();

    @PostConstruct
    public void init() {
        // Pre-load a default admin user with a hashed password
        // The plain-text password for "admin" is "Admin123!" but stored hashed
        String adminHash = passwordEncoder.encode("Admin123!");
        users.put("admin", new User("admin", adminHash, "ROLE_ADMIN"));

        // Pre-load a regular user
        String userHash = passwordEncoder.encode("User123!");
        users.put("user1", new User("user1", userHash, "ROLE_USER"));

        System.out.println("UserDetailsService initialized with default users.");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.get(username.toLowerCase());
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole()))
        );
    }

    /**
     * Register a new user. Password is hashed with BCrypt before storing.
     * Returns false if the username already exists.
     */
    public boolean registerUser(String username, String plainPassword, String role) {
        if (users.containsKey(username.toLowerCase())) {
            return false;
        }
        String hash = passwordEncoder.encode(plainPassword);
        users.put(username.toLowerCase(), new User(username.toLowerCase(), hash, role));
        return true;
    }

    public boolean userExists(String username) {
        return users.containsKey(username.toLowerCase());
    }
}
