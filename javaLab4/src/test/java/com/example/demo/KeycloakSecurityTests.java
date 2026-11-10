package com.example.demo;

import com.example.demo.config.KeycloakSecurityConfiguration;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:keycloak-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.oidc.issuer=http://idp.example/realms/test", "app.jwt.secret="
})
@AutoConfigureMockMvc
@ActiveProfiles({"keycloak", "test"})
@Import(KeycloakSecurityTests.Keys.class)
class KeycloakSecurityTests {
    static final String ISSUER = "http://idp.example/realms/test";
    static final KeyPair KEY = generateKey();
    @Autowired MockMvc mvc;

    static KeyPair generateKey() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
    }

    @TestConfiguration
    static class Keys {
        @Bean @Primary
        JwtDecoder testDecoder() {
            var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
            decoder.setJwtValidator(KeycloakSecurityConfiguration.validator(ISSUER, "routing-api"));
            return decoder;
        }
    }

    String token(String issuer, List<String> audience, String type, Instant expiry, Map<String, Object> extra, KeyPair key) {
        var rsa = new RSAKey.Builder((RSAPublicKey) key.getPublic()).privateKey((RSAPrivateKey) key.getPrivate()).keyID("test").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("idp-subject-123")
                .audience(audience).issuedAt(expiry.minusSeconds(300)).expiresAt(expiry)
                .claim("typ", type).claim("preferred_username", "external-user");
        extra.forEach(claims::claim);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims.build())).getTokenValue();
    }

    String role(String role) {
        return token(ISSUER, List.of("routing-api"), "Bearer", Instant.now().plusSeconds(300),
                Map.of("realm_access", Map.of("roles", List.of(role))), KEY);
    }

    @Test
    void realmRolesAuthorizeWithoutLocalAccounts() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + role("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("idp-subject-123"))
                .andExpect(jsonPath("$.username").value("external-user"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(get("/api/v1/deliveries").header("Authorization", "Bearer " + role("DRIVER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + role("DRIVER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/scenarios").header("Authorization", "Bearer " + role("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"OIDC\",\"vehicleIds\":[],\"deliveryIds\":[]}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/scenarios").header("Authorization", "Bearer " + role("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void audienceClientRolesMapButOtherClientsAndUnknownRolesDoNot() throws Exception {
        String client = token(ISSUER, List.of("routing-api"), "Bearer", Instant.now().plusSeconds(300),
                Map.of("resource_access", Map.of("routing-api", Map.of("roles", List.of("ADMIN")))), KEY);
        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + client)).andExpect(status().isOk());
        for (Map<String, Object> claims : List.<Map<String, Object>>of(
                Map.of("resource_access", Map.of("unrelated-client", Map.of("roles", List.of("ADMIN")))),
                Map.of("realm_access", Map.of("roles", List.of("admin", "ROLE_ADMIN", "offline_access"))),
                Map.of("realm_access", "malformed"))) {
            String jwt = token(ISSUER, List.of("routing-api"), "Bearer", Instant.now().plusSeconds(300), claims, KEY);
            mvc.perform(get("/api/customers").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        }
    }

    @Test
    void wrongIssuerAudienceSignatureExpiredAndIdTokensAreRejected() throws Exception {
        Instant future = Instant.now().plusSeconds(300);
        for (String jwt : List.of(
                token("http://untrusted.example", List.of("routing-api"), "Bearer", future, Map.of(), KEY),
                token(ISSUER, List.of("other-api"), "Bearer", future, Map.of(), KEY),
                token(ISSUER, List.of(), "Bearer", future, Map.of(), KEY),
                token(ISSUER, List.of("routing-api"), "ID", future, Map.of(), KEY),
                token(ISSUER, List.of("routing-api"), "Bearer", Instant.now().minusSeconds(120), Map.of(), KEY),
                token(ISSUER, List.of("routing-api"), "Bearer", future, Map.of(), generateKey()))) {
            mvc.perform(get("/api/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void swaggerUsesAuthorizationCodeAndLocalLoginIsUnavailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl")
                        .value(ISSUER + "/protocol/openid-connect/auth"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.scopes.openid").exists())
                .andExpect(jsonPath("$.paths['/login']").doesNotExist())
                .andExpect(jsonPath("$.paths['/refresh']").doesNotExist());
        mvc.perform(get("/api/vehicles")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").header("Authorization", "Bearer " + role("ADMIN"))).andExpect(status().isNotFound());
    }
}
