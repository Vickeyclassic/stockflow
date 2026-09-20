package com.stockflow;

import com.stockflow.config.ProductionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:production_release;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.password=test-only", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
    "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect", "app.cors.allowed-origins=https://stockflow.example"})
@AutoConfigureMockMvc @ActiveProfiles({"test", "prod"})
class ProductionReleaseTests {
    @Autowired MockMvc mvc;
    @Test void migratedSchemaStartsAndProductionDocsAreDisabled() throws Exception {
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(options("/api/products").header("Origin", "https://stockflow.example")
            .header("Access-Control-Request-Method", "GET")).andExpect(status().isOk());
        mvc.perform(options("/api/products").header("Origin", "https://untrusted.example")
            .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
    }
    @Test void productionRejectsWildcardsAndEmptyCredentials() {
        for (String origin : new String[]{"*", "", "https://*.example", "https://example/path", "https://user@example"})
            assertThrows(IllegalArgumentException.class, () -> new ProductionConfig(origin, "stockflow", "test-only"));
        assertThrows(IllegalArgumentException.class, () -> new ProductionConfig("https://example", "stockflow", ""));
        assertDoesNotThrow(() -> new ProductionConfig("https://example,http://localhost:8080", "stockflow", "test-only"));
    }
}
