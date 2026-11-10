package com.example.demo;

import com.example.demo.dto.ScenarioRequest;
import com.example.demo.service.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mixed-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "prod", "test"})
class MixedProfileSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired ScenarioService scenarios;

    @Test
    void prodWinsIfBothProfilesAreAccidentallySelected() throws Exception {
        mvc.perform(post("/api/scenarios")).andExpect(status().isUnauthorized());
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> scenarios.create(new ScenarioRequest("Denied", Set.of(), Set.of())));
    }
}
