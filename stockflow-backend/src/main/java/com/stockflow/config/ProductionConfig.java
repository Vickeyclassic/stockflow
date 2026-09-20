package com.stockflow.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fail closed on blank database credentials or broad/invalid production CORS origins. */
@Configuration
@Profile("prod")
public class ProductionConfig {
    public ProductionConfig(@Value("${app.cors.allowed-origins}") String origins,
                            @Value("${spring.datasource.username}") String username,
                            @Value("${spring.datasource.password}") String password) {
        if (username.isBlank() || password.isBlank())
            throw new IllegalArgumentException("Production database credentials must be nonblank");
        for (String value : origins.split(",", -1)) {
            URI origin;
            try { origin = URI.create(value.strip()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Production CORS requires exact HTTP(S) origins"); }
            if (!("http".equals(origin.getScheme()) || "https".equals(origin.getScheme())) || origin.getHost() == null
                    || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                    || (origin.getPath() != null && !origin.getPath().isEmpty()))
                throw new IllegalArgumentException("Production CORS requires exact HTTP(S) origins without paths or wildcards");
        }
    }
}
