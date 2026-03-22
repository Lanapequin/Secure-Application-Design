package com.eci.secureapp.model;

/**
 * Simple User model. In a real application this would be stored in a database.
 * Passwords are stored as BCrypt hashes - never in plain text.
 */
public class User {

    private String username;
    private String passwordHash; // BCrypt hash - NEVER plain text
    private String role;

    public User() {}

    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
