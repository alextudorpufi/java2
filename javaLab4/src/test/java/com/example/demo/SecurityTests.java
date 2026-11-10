package com.example.demo;

import com.example.demo.dto.ScenarioRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.ScenarioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SecurityTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired DriverRepository drivers;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired ScenarioService scenarios;
    final JsonMapper json = JsonMapper.builder().build();
    static final String PASSWORD = "a-test-password-123";

    @BeforeEach
    void accounts() {
        for (Role role : Role.values()) {
            String username = role.name().toLowerCase();
            var user = users.findByUsername(username)
                    .orElseGet(() -> new AppUser(username, passwords.encode(PASSWORD), role, true));
            user.setEnabled(true);
            user.getRoles().clear();
            user.getRoles().add(role);
            users.save(user);
        }
    }

    String token(String username) throws Exception {
        var response = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }

    String registration(String username) {
        return """
                {"username":"%s","password":"%s","customer":{
                "name":"Registered","email":"%s@example.com","phone":"0700000000",
                "address":"Bucharest","latitude":44,"longitude":26}}
                """.formatted(username, PASSWORD, username);
    }

    @Test
    void registrationCreatesCustomerAccountWithBcryptAndCannotGrantAdmin() throws Exception {
        String name = "new-" + UUID.randomUUID();
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name).replace("\"username\":", "\"roles\":[\"ADMIN\"],\"username\":")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        var account = users.findByUsername(name).orElseThrow();
        assertTrue(account.getPasswordHash().startsWith("$2"));
        assertTrue(passwords.matches(PASSWORD, account.getPasswordHash()));
        assertEquals(Set.of(Role.CUSTOMER), account.getRoles());
        assertEquals(account.getId(), customers.findByEmail(name + "@example.com").orElseThrow().getUser().getId());
        String jwt = token(name);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(name));
        mvc.perform(post("/api/scenarios").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registration(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationValidatesInputAndRollsBackDuplicateProfile() throws Exception {
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        String name = "valid-" + UUID.randomUUID();
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name).replace(PASSWORD, "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name).replace(PASSWORD, "é".repeat(40))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registration(name)))
                .andExpect(status().isCreated());
        String second = "other-" + UUID.randomUUID();
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name).replace("\"username\":\"" + name, "\"username\":\"" + second)))
                .andExpect(status().isConflict());
        assertFalse(users.existsByUsername(second));
    }

    @Test
    void onlyAdminCanCreateDriverAndDriverCanLogIn() throws Exception {
        String name = "driver-" + UUID.randomUUID();
        String body = """
                {"username":"%s","password":"%s","name":"Driver","email":"%s@example.com","phone":"0700000000"}
                """.formatted(name, PASSWORD, name);
        mvc.perform(post("/api/users/drivers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/drivers").header("Authorization", "Bearer " + token("customer"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/drivers").header("Authorization", "Bearer " + token("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("DRIVER"));
        assertTrue(drivers.findAll().stream().anyMatch(driver -> driver.getEmail().equals(name + "@example.com")));
        mvc.perform(get("/api/deliveries").header("Authorization", "Bearer " + token(name)))
                .andExpect(status().isOk());
    }

    @Test
    void publicReadsAndPrivateDataFollowPolicy() throws Exception {
        for (String path : new String[]{"/api/vehicles", "/api/v1/vehicles", "/v3/api-docs", "/swagger-ui/index.html"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/customers", "/api/v1/customers", "/api/deliveries",
                "/api/v1/scenarios", "/api/me", "/actuator/health", "/unknown"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + token("driver")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/deliveries").header("Authorization", "Bearer " + token("customer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk());
    }

    @Test
    void allCrudWritesRequireAdminAndCannotUseBasicAuthentication() throws Exception {
        String customer = token("customer");
        String driver = token("driver");
        for (String prefix : new String[]{"/api/", "/api/v1/"}) {
            for (String resource : new String[]{"customers", "vehicles", "deliveries", "scenarios"}) {
                for (var builder : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                        post(prefix + resource), put(prefix + resource + "/1"), delete(prefix + resource + "/1")}) {
                    mvc.perform(builder).andExpect(status().isUnauthorized());
                    mvc.perform(builder.header("Authorization", "Bearer " + customer)).andExpect(status().isForbidden());
                }
                mvc.perform(delete(prefix + resource + "/1").header("Authorization", "Bearer " + driver))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/api/customers").with(httpBasic("admin", PASSWORD))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/scenarios").header("Authorization", "Bearer " + token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"JWT write\",\"vehicleIds\":[],\"deliveryIds\":[]}"))
                .andExpect(status().isCreated()).andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void invalidCredentialsDisabledUsersAndInvalidTokensAreRejected() throws Exception {
        for (String username : new String[]{"admin", "missing"}) {
            mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid credentials"));
        }
        String jwt = token("admin");
        var account = users.findByUsername("admin").orElseThrow();
        account.setEnabled(false);
        users.save(account);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        for (String bad : new String[]{"not-a-jwt", signed("customer", "javaLab4", Instant.now().minusSeconds(120)),
                signed("customer", "wrong-issuer", Instant.now().plusSeconds(600)),
                jwt.substring(0, jwt.lastIndexOf('.') + 1) + "A".repeat(43)}) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + bad)).andExpect(status().isUnauthorized());
        }
    }

    String signed(String subject, String issuer, Instant expiry) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(subject).issuer(issuer).issuedAt(expiry.minusSeconds(900))
                        .expiresAt(expiry).claim("roles", java.util.List.of("ADMIN")).build())).getTokenValue();
    }

    @Test
    void rolesComeFromDatabaseAndChangesApplyToExistingTokens() throws Exception {
        String jwt = signed("customer", "javaLab4", Instant.now().plusSeconds(600));
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        String adminJwt = token("admin");
        var account = users.findByUsername("admin").orElseThrow();
        account.getRoles().clear();
        account.getRoles().add(Role.CUSTOMER);
        users.save(account);
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void methodSecurityRejectsDirectServiceInvocation() {
        assertThrows(AccessDeniedException.class,
                () -> scenarios.create(new ScenarioRequest("Forbidden", Set.of(), Set.of())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void methodSecurityAllowsAdminDirectServiceInvocation() {
        assertNotNull(scenarios.create(new ScenarioRequest("Allowed", Set.of(), Set.of())).id());
    }
}
