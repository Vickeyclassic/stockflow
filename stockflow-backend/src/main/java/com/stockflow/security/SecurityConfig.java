package com.stockflow.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must be Base64 of at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, com.stockflow.repository.UserRepository users) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator("stockflow"),
            jwt -> users.findByUsername(jwt.getSubject())
                .filter(user -> user.isActive() && user.getRole().name().equals(jwt.getClaimAsString("role")))
                .map(user -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success())
                .orElseGet(() -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Account is unavailable or role changed", null)))));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, error) -> {
            response.setStatus(401); response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Sign in again. Your session is missing, invalid, or expired.\"}");
        };
        AccessDeniedHandler forbidden = (request, response, error) -> {
            response.setStatus(403); response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"Your role cannot perform this action.\"}");
        };
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role"); authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/health/ready", "/api/version").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "STAFF")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)).build();
    }
}
