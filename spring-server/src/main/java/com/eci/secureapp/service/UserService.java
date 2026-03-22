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
 * User store backed by an in-memory map.
 * Passwords are always stored as BCrypt hashes.
 *
 * In a real application this would talk to a database,
 * but for this workshop an in-memory store is sufficient.
 */
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private PasswordEncoder encoder;

    private final Map<String, User> store = new HashMap<>();

    /** Pre-load two users when the application starts */
    @PostConstruct
    public void init() {
        // BCrypt hashes — plain text is never stored anywhere
        register("admin", "Admin123!", "ROLE_ADMIN");
        register("student", "Student123!", "ROLE_USER");
        System.out.println("UserService ready — 2 default users loaded.");
    }

    /** Called by Spring Security during authentication */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = store.get(username.toLowerCase());
        if (u == null) throw new UsernameNotFoundException("User not found: " + username);
        return new org.springframework.security.core.userdetails.User(
                u.getUsername(),
                u.getPasswordHash(),
                List.of(new SimpleGrantedAuthority(u.getRole()))
        );
    }

    /**
     * Registers a new user.
     * @return false if username already taken, true if created.
     */
    public boolean register(String username, String plainPassword, String role) {
        String key = username.toLowerCase();
        if (store.containsKey(key)) return false;
        store.put(key, new User(key, encoder.encode(plainPassword), role));
        return true;
    }

    public boolean exists(String username) {
        return store.containsKey(username.toLowerCase());
    }
}
