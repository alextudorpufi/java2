package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.service.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:refresh;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired RefreshTokenService refreshService;
    final JsonMapper json = JsonMapper.builder().build();
    AppUser user;

    @BeforeEach
    void account() {
        user = users.save(new AppUser("refresh-" + UUID.randomUUID(), passwords.encode("refresh-password-123"), Role.ADMIN, true));
    }

    JsonNode login() throws Exception {
        var result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user.getUsername(), "password", "refresh-password-123"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()).andExpect(jsonPath("$.refreshExpiresIn").isNumber())
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    String body(String raw) { return json.writeValueAsString(Map.of("refreshToken", raw)); }

    JsonNode rotate(String raw) throws Exception {
        var result = mvc.perform(post("/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    void rejected(String raw) throws Exception {
        mvc.perform(post("/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotationReturnsNewPairWithHashedStorageAndUsableAccessToken() throws Exception {
        var first = login();
        String refresh = first.get("refreshToken").asText();
        assertEquals(43, refresh.length());
        var hashes = jdbc.queryForList("select token_hash from refresh_tokens where session_id in (select id from refresh_sessions where user_id=?)",
                String.class, user.getId());
        assertEquals(1, hashes.size());
        assertEquals(64, hashes.getFirst().length());
        assertNotEquals(refresh, hashes.getFirst());
        var second = rotate(refresh);
        assertNotEquals(refresh, second.get("refreshToken").asText());
        assertNotEquals(first.get("accessToken").asText(), second.get("accessToken").asText());
        assertTrue(second.get("refreshExpiresIn").asLong() <= first.get("refreshExpiresIn").asLong());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + second.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(user.getUsername()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + second.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        rejected(first.get("accessToken").asText());
    }

    @Test
    void replayRevokesTheFamilyButNotOtherLogins() throws Exception {
        String first = login().get("refreshToken").asText();
        String independent = login().get("refreshToken").asText();
        String second = rotate(first).get("refreshToken").asText();
        rejected(first);
        rejected(second);
        rotate(independent);
    }

    @Test
    void logoutRevokesSessionAndIsIdempotent() throws Exception {
        String first = login().get("refreshToken").asText();
        String second = rotate(first).get("refreshToken").asText();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/logout").contentType(MediaType.APPLICATION_JSON).content(body(first)))
                    .andExpect(status().isNoContent());
        }
        rejected(second);
        mvc.perform(post("/logout").contentType(MediaType.APPLICATION_JSON).content(body("unknown-token")))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredDisabledAndUnknownRefreshTokensAreRejected() throws Exception {
        String expired = login().get("refreshToken").asText();
        jdbc.update("update refresh_sessions set expires_at = ? where user_id = ?",
                java.time.OffsetDateTime.now().minusDays(1), user.getId());
        rejected(expired);
        String disabled = login().get("refreshToken").asText();
        user.setEnabled(false);
        users.save(user);
        rejected(disabled);
        rejected("unknown-token");
        mvc.perform(post("/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshedTokensUseCurrentDatabaseRoles() throws Exception {
        String first = login().get("refreshToken").asText();
        user.getRoles().clear();
        user.getRoles().add(Role.CUSTOMER);
        users.save(user);
        String access = rotate(first).get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(post("/api/scenarios").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentRefreshHasOneWinnerAndReplayRevokesItsReplacement() throws Exception {
        String first = login().get("refreshToken").asText();
        var start = new CountDownLatch(1);
        Callable<RefreshTokenService.TokenPair> attempt = () -> {
            start.await();
            try { return refreshService.refresh(first); }
            catch (BadCredentialsException expected) { return null; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(attempt);
            var b = executor.submit(attempt);
            start.countDown();
            var results = Arrays.asList(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            var winners = results.stream().filter(Objects::nonNull).toList();
            assertEquals(1, winners.size());
            rejected(winners.getFirst().refreshToken());
        }
    }
}
