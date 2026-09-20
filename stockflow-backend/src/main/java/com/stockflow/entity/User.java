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
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
    protected User() {}
    public User(String username, String passwordHash, Role role) {
        this.username = username; this.passwordHash = passwordHash; this.role = role;
    }
    public String getUsername() { return username; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
