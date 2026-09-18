package com.stockflow;

import com.stockflow.entity.User;
import com.stockflow.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired JwtEncoder encoder;
    private final String password = "test-only-long-password";
    @BeforeEach void setup() {
        users.deleteAll();
        users.save(new User("admin", passwords.encode(password), User.Role.ADMIN));
        users.save(new User("staff", passwords.encode(password), User.Role.STAFF));
    }
    private String login(String username) throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",username,"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(username))
            .andExpect(jsonPath("$.expiresAt").exists()).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void loginAndCurrentUserAndBadCredentials() throws Exception {
        var token=login("admin");
        assertNotEquals(password,users.findByUsername("admin").orElseThrow().getPasswordHash());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void protectedEndpointRejectsMissingTamperedAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization","Bearer invalid.token.value")).andExpect(status().isUnauthorized());
        var expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("stockflow").subject("admin").claim("role","ADMIN")
            .issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(60)).build())).getTokenValue();
        mvc.perform(get("/api/products").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
    }
    @Test void adminCanCreateUsersAndDeleteMasterData() throws Exception {
        var token=login("admin");
        mvc.perform(post("/api/users").header("Authorization","Bearer "+token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username","new.staff","password",password,"role","STAFF"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STAFF"));
        var created=mvc.perform(post("/api/categories").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"name\":\"Auth test category\"}")).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(delete("/api/categories/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
    }
    @Test void staffCanReadAndCreateButCannotDeleteOrManageUsers() throws Exception {
        var token=login("staff");
        mvc.perform(get("/api/products").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        var result=mvc.perform(post("/api/categories").header("Authorization","Bearer "+token).contentType("application/json")
            .content("{\"name\":\"Staff category\"}")).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(delete("/api/categories/"+id).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header("Authorization","Bearer "+token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username","forbidden","password",password,"role","ADMIN"))))
            .andExpect(status().isForbidden());
        assertTrue(users.findByUsername("forbidden").isEmpty());
    }
}
