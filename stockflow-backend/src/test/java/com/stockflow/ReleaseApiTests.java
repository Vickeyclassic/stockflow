package com.stockflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ReleaseApiTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Test void documentationCoversAllApisAndPermissionsWithoutHashes() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var spec = json.readTree(response.getResponse().getContentAsString());
        for (String path : new String[]{"auth/login", "auth/me", "users", "users/{id}/active", "products",
                "suppliers", "customers", "sales-orders", "purchase-orders", "inventory/transactions", "dashboard"})
            assertTrue(spec.get("paths").has("/api/" + path), path);
        assertTrue(spec.at("/paths/~1api~1products~1{id}/delete/description").asText().startsWith("Permission: ADMIN only."));
        assertEquals("ADMIN", spec.at("/paths/~1api~1users/get/x-roles/0").asText());
        assertEquals(2, spec.at("/paths/~1api~1products/get/x-roles").size());
        assertEquals(0, spec.at("/paths/~1api~1auth~1login/post/security").size());
        assertEquals("http", spec.at("/components/securitySchemes/bearerAuth/type").asText());
        assertTrue(spec.at("/components/schemas/Login/properties/password/writeOnly").asBoolean());
        assertTrue(spec.at("/paths/~1api~1products/post/responses").has("201"));
        assertTrue(spec.at("/paths/~1api~1products~1{id}/delete/responses").has("204"));
        assertFalse(response.getResponse().getContentAsString().contains("passwordHash"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
    @Test void releaseInfoIsMinimalAndNormalApisStayProtected() throws Exception {
        var response = mvc.perform(get("/api/version")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.version").value("1.3.0"))
            .andExpect(jsonPath("$.service").value("stockflow-backend"))
            .andExpect(jsonPath("$.builtAt").isNotEmpty()).andReturn();
        assertEquals(3, json.readTree(response.getResponse().getContentAsString()).size());
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
