package com.stockflow.security;

import com.stockflow.entity.User;
import com.stockflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("dev")
public class DevelopmentAdmin implements CommandLineRunner {
    private final UserRepository users;
    private final String username, hash;
    public DevelopmentAdmin(UserRepository users,
        @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String username,
        @Value("${BOOTSTRAP_ADMIN_PASSWORD_HASH:}") String hash) {
        this.users=users; this.username=username; this.hash=hash;
    }
    @Override public void run(String... args) {
        if (hash.isBlank() || users.existsByRole(User.Role.ADMIN)) return;
        if (!username.matches("[a-z0-9._-]{3,100}") || !hash.matches("\\$2[aby]\\$1[0-6]\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("Bootstrap requires a lowercase username and BCrypt hash (cost 10–16)");
        users.saveAndFlush(new User(username,hash,User.Role.ADMIN));
    }
}
