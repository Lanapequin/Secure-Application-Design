package com.workshop.secure_app.service;

import com.workshop.secure_app.model.User;
import com.workshop.secure_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword()) // Ya está hasheado en BD
                .roles(user.getRole())
                .build();
    }

    public User registerUser(String username, String rawPassword) {
        // Hashear contraseña con BCrypt ANTES de guardar
        String hashedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(username, hashedPassword, "USER");
        return userRepository.save(user);
    }
}