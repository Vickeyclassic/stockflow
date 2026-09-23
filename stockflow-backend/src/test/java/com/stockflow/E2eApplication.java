package com.stockflow;

import com.stockflow.entity.User;
import com.stockflow.repository.UserRepository;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test-classpath-only launcher. Always uses a fresh in-memory database, never a user's datasource. */
public final class E2eApplication {
    public static void main(String[] args) {
        if (!"true".equals(System.getenv("E2E_ALLOW_WRITES")) || System.getenv("E2E_PASSWORD") == null)
            throw new IllegalStateException("Use the Playwright local test runner");
        String database = "jdbc:h2:mem:e2e_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        new SpringApplication(StockflowApplication.class, Bootstrap.class).run(
            "--spring.profiles.active=test", "--spring.profiles.include=", "--server.address=127.0.0.1", "--server.port=18080",
            "--spring.datasource.url=" + database,
            "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
            "--spring.datasource.password=", "--spring.flyway.enabled=true", "--spring.flyway.baseline-on-migrate=false",
            "--spring.flyway.url=" + database, "--spring.flyway.user=sa", "--spring.flyway.password=",
            "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
            "--app.cors.allowed-origins=http://127.0.0.1:4173", "--app.demo.enabled=false",
            "--springdoc.api-docs.enabled=false", "--springdoc.swagger-ui.enabled=false");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Bootstrap {
        @Bean ApplicationRunner e2eAdmin(UserRepository users, PasswordEncoder passwords) {
            return args -> {
                users.saveAndFlush(new User(System.getenv("E2E_USERNAME"), passwords.encode(System.getenv("E2E_PASSWORD")), User.Role.ADMIN));
                System.out.println("STOCKFLOW_E2E_READY");
            };
        }
    }
}
