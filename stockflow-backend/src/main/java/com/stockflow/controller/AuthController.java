package com.stockflow.controller;

import com.stockflow.entity.User;
import com.stockflow.repository.UserRepository;
import com.stockflow.exception.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    public record Login(@NotBlank @Size(max=100) String username, @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY, format="password") @NotBlank @Size(max=72) String password) {}
    public record CreateUser(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{3,100}") String username,
        @io.swagger.v3.oas.annotations.media.Schema(accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY, format="password") @NotBlank @Size(min=12,max=72) String password, @NotNull User.Role role) {}
    public record Profile(String username, User.Role role) {}
    public record Session(String token, Instant expiresAt, Profile user) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final long ttl;
    private final String dummyHash;
    public AuthController(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
        @Value("${app.jwt.ttl-seconds}") long ttl) {
        if (ttl < 60 || ttl > 86400) throw new IllegalArgumentException("JWT_TTL_SECONDS must be 60–86400");
        this.users=users; this.passwords=passwords; this.encoder=encoder; this.ttl=ttl;
        this.dummyHash=passwords.encode(java.util.UUID.randomUUID().toString());
    }
    @PostMapping("/auth/login") public ResponseEntity<Session> login(@Valid @RequestBody Login request) {
        var user=users.findByUsername(request.username().strip().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches=request.password().getBytes(StandardCharsets.UTF_8).length <= 72
            && passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null || !user.isActive()) throw new DomainException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid username or password");
        var now=Instant.now(); var expires=now.plusSeconds(ttl);
        var claims=JwtClaimsSet.builder().issuer("stockflow").subject(user.getUsername()).issuedAt(now)
            .expiresAt(expires).claim("role",user.getRole().name()).build();
        var token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Session(token,expires,new Profile(user.getUsername(),user.getRole())));
    }
    @GetMapping("/auth/me") public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return new Profile(jwt.getSubject(), User.Role.valueOf(jwt.getClaimAsString("role")));
    }
    @PostMapping("/users") public ResponseEntity<Profile> create(@Valid @RequestBody CreateUser request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new DomainException(HttpStatus.BAD_REQUEST,"INVALID_PASSWORD","Password must not exceed 72 UTF-8 bytes");
        var user=users.saveAndFlush(new User(request.username().toLowerCase(Locale.ROOT),passwords.encode(request.password()),request.role()));
        return ResponseEntity.status(201).body(new Profile(user.getUsername(),user.getRole()));
    }
}
