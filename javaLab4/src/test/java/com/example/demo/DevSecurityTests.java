package com.example.demo;

import com.example.demo.dto.ScenarioRequest;
import com.example.demo.service.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dev-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class DevSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired ScenarioService scenarios;

    @Test
    void devBypassesHttpAndMethodSecurityWithoutCredentialsOrCsrf() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isOk());
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dev\",\"vehicleIds\":[],\"deliveryIds\":[]}"))
                .andExpect(status().isCreated());
        assertNotNull(scenarios.create(new ScenarioRequest("Direct dev call", Set.of(), Set.of())).id());
    }
}
