package com.eci.secureapp.model;

public class User {
    private String username;
    private String passwordHash;
    private String role;

    public User() {}
    public User(String username, String passwordHash, String role) {
        this.username     = username;
        this.passwordHash = passwordHash;
        this.role         = role;
    }

    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole()         { return role; }

    public void setUsername(String u)     { this.username = u; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public void setRole(String r)         { this.role = r; }
}
