package com.stockflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class User extends AuditedEntity {
    public enum Role { ADMIN, STAFF }
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Role role;
    protected User() {}
    public User(String username, String passwordHash, Role role) {
        this.username = username; this.passwordHash = passwordHash; this.role = role;
    }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
