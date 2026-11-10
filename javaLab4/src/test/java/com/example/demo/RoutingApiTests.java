package com.example.demo;

import com.example.demo.entity.Depot;
import com.example.demo.repository.DepotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:routing-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class RoutingApiTests {
    @Autowired MockMvc mvc;
    @Autowired DepotRepository depots;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    private Long depotId;

    @BeforeEach
    void setup() {
        for (String table : new String[]{"scenario_vehicles", "scenario_deliveries", "scenarios",
                "deliveries", "vehicles", "drivers", "customers", "depots"}) {
            jdbc.update("delete from " + table);
        }
        depotId = depots.save(new Depot("Only depot", BigDecimal.ZERO, BigDecimal.ZERO)).getId();
    }

    private String vehicle(String name) {
        return """
                {"name":"%s","capacity":100,"depotId":%d,"available":true}
                """.formatted(name, depotId);
    }

    private long create(String resource, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/" + resource).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long customer() throws Exception {
        return create("customers", """
                {"name":"Ana","email":"ana@example.com","phone":"0700000000",
                 "address":"Bucharest","latitude":44.4268,"longitude":26.1025}
                """);
    }

    private String delivery(long customerId) {
        return """
                {"customerId":%d,"quantity":10,"notes":"Fragile","timestamp":"2026-11-02T10:00:00Z"}
                """.formatted(customerId);
    }

    private String scenario(String name, long vehicleId, long deliveryId) {
        return """
                {"name":"%s","vehicleIds":[%d],"deliveryIds":[%d]}
                """.formatted(name, vehicleId, deliveryId);
    }

    @Test
    void crudAndManyToManyMembershipSurviveSeparateRequests() throws Exception {
        long customerId = customer();
        long vehicleId = create("vehicles", vehicle("Van"));
        long deliveryId = create("deliveries", delivery(customerId));
        long first = create("scenarios", scenario("Morning", vehicleId, deliveryId));
        long second = create("scenarios", scenario("Alternative", vehicleId, deliveryId));
        for (String resource : new String[]{"vehicles", "deliveries", "scenarios"}) {
            mvc.perform(get("/api/" + resource)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").isNumber());
        }
        mvc.perform(get("/api/scenarios/" + second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleIds[0]").value(vehicleId))
                .andExpect(jsonPath("$.deliveryIds[0]").value(deliveryId));
        mvc.perform(put("/api/vehicles/" + vehicleId).contentType(MediaType.APPLICATION_JSON)
                .content(vehicle("Renamed van"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed van"));
        mvc.perform(put("/api/deliveries/" + deliveryId).contentType(MediaType.APPLICATION_JSON)
                .content(delivery(customerId).replace("\"quantity\":10", "\"quantity\":20")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(20));
        mvc.perform(delete("/api/vehicles/" + vehicleId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/deliveries/" + deliveryId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/customers/" + customerId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/scenarios/" + first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/vehicles/" + vehicleId)).andExpect(status().isOk());
        mvc.perform(get("/api/deliveries/" + deliveryId)).andExpect(status().isOk());
        mvc.perform(get("/api/scenarios/" + second)).andExpect(status().isOk());
        mvc.perform(put("/api/scenarios/" + second).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Empty\",\"vehicleIds\":[],\"deliveryIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicleIds").isEmpty())
                .andExpect(jsonPath("$.deliveryIds").isEmpty());
        mvc.perform(delete("/api/vehicles/" + vehicleId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/deliveries/" + deliveryId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/customers/" + customerId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/scenarios/" + second)).andExpect(status().isNoContent());
        for (String path : new String[]{"vehicles/" + vehicleId, "deliveries/" + deliveryId, "scenarios/" + second}) {
            mvc.perform(get("/api/" + path)).andExpect(status().isNotFound());
            mvc.perform(delete("/api/" + path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void validationMissingReferencesAndFailedUpdateAreHandled() throws Exception {
        for (String resource : new String[]{"vehicles", "deliveries", "scenarios"}) {
            mvc.perform(post("/api/" + resource).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        }
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(vehicle("Bad").replace("\"capacity\":100", "\"capacity\":0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                .content(vehicle("Missing depot").replace("\"depotId\":" + depotId, "\"depotId\":999999")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/deliveries").contentType(MediaType.APPLICATION_JSON).content(delivery(999999)))
                .andExpect(status().isNotFound());
        long id = create("scenarios", "{\"name\":\"Original\",\"vehicleIds\":[],\"deliveryIds\":[]}");
        mvc.perform(put("/api/scenarios/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(scenario("Invalid", 999999, 999999)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Vehicle not found: 999999"));
        mvc.perform(get("/api/scenarios/" + id)).andExpect(jsonPath("$.name").value("Original"));
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad\",\"vehicleIds\":[null],\"deliveryIds\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/scenarios").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/scenarios/invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/scenarios/" + id).accept("text/plain")).andExpect(status().isNotAcceptable());
    }

    @Test
    void etagsSupportConditionalJsonXmlListsAndChanges() throws Exception {
        long vehicleId = create("vehicles", vehicle("Van"));
        long customerId = customer();
        long deliveryId = create("deliveries", delivery(customerId));
        long scenarioId = create("scenarios", scenario("Morning", vehicleId, deliveryId));
        for (String resource : new String[]{"customers", "vehicles", "deliveries", "scenarios"}) {
            String listTag = mvc.perform(get("/api/" + resource)).andExpect(status().isOk())
                    .andExpect(header().exists("ETag")).andReturn().getResponse().getHeader("ETag");
            mvc.perform(get("/api/" + resource).header("If-None-Match", listTag))
                    .andExpect(status().isNotModified()).andExpect(content().string(""));
        }
        String url = "/api/scenarios/" + scenarioId;
        String jsonTag = mvc.perform(get(url).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");
        assertNotNull(jsonTag);
        mvc.perform(get(url).header("If-None-Match", "\"other\", " + jsonTag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(get(url).header("If-None-Match", "*"))
                .andExpect(status().isNotModified());
        String xmlTag = mvc.perform(get(url).accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/ScenarioResponse/name").string("Morning"))
                .andReturn().getResponse().getHeader("ETag");
        assertNotEquals(jsonTag, xmlTag);
        mvc.perform(get(url).accept(MediaType.APPLICATION_XML).header("If-None-Match", xmlTag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(get(url).accept(MediaType.APPLICATION_XML).header("If-None-Match", jsonTag))
                .andExpect(status().isOk());
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                .content(scenario("Evening", vehicleId, deliveryId))).andExpect(status().isOk());
        mvc.perform(get(url).header("If-None-Match", jsonTag)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Evening"));
        mvc.perform(delete(url)).andExpect(status().isNoContent());
        mvc.perform(get(url).header("If-None-Match", "*")).andExpect(status().isNotFound());
    }

    @Test
    void xmlRequestsAndOpenApiDocumentationWork() throws Exception {
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML)
                .content("<VehicleRequest><name>XML van</name><capacity>50</capacity><depotId>" + depotId
                        + "</depotId><available>true</available></VehicleRequest>"))
                .andExpect(status().isCreated()).andExpect(xpath("/VehicleResponse/name").string("XML van"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/scenarios/{id}'].get.summary").value("Read a scenario"))
                .andExpect(jsonPath("$.paths['/api/scenarios/{id}'].get.responses['304']").exists())
                .andExpect(jsonPath("$.paths['/api/scenarios/{id}'].get.responses['304'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/scenarios'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/scenarios/{id}'].get.parameters[?(@.name == 'If-None-Match')]").isNotEmpty());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void versionedCustomerLinksAreFollowableAndScopeDeliveries() throws Exception {
        long customerId = customer();
        long deliveryId = create("deliveries", delivery(customerId));
        long other = create("customers", """
                {"name":"Other","email":"other@example.com","phone":"0700000001",
                 "address":"Other address","latitude":44,"longitude":26}
                """);
        create("deliveries", delivery(other));
        String path = "/api/v1/customers/" + customerId;
        var response = mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost" + path))
                .andExpect(jsonPath("$._links.collection.href").value("http://localhost/api/v1/customers"))
                .andReturn().getResponse();
        String deliveriesLink = json.readTree(response.getContentAsString())
                .get("_links").get("deliveries").get("href").asText();
        String etag = mvc.perform(get(java.net.URI.create(deliveriesLink))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(deliveryId))
                .andExpect(jsonPath("$[0].customerId").value(customerId))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get(java.net.URI.create(deliveriesLink)).header("If-None-Match", etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/customers/" + other + "/deliveries").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
        mvc.perform(get("/api/v1/customers/999999/deliveries")).andExpect(status().isNotFound());
        mvc.perform(get(path).accept(MediaType.APPLICATION_XML)).andExpect(status().isOk())
                .andExpect(xpath("/CustomerResponse/_links/self/href").string("http://localhost" + path));
        mvc.perform(get("/api/v1/customers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._links.deliveries.href").exists());
    }

    @Test
    void versionOneCrudCoexistsWithLegacyAndUnknownVersionsAreRejected() throws Exception {
        long id = create("v1/customers", """
                {"name":"Versioned","email":"v1@example.com","phone":"0700000000",
                 "address":"Bucharest","latitude":44,"longitude":26}
                """);
        String payload = """
                {"name":"Updated","email":"v1@example.com","phone":"0700000000",
                 "address":"Bucharest","latitude":44,"longitude":26}
                """;
        mvc.perform(put("/api/v1/customers/" + id).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$._links.self.href").exists());
        mvc.perform(get("/api/customers/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Link", "<http://localhost/api/v1/customers/" + id + ">; rel=\"successor-version\""))
                .andExpect(jsonPath("$.name").value("Updated"));
        long vehicleId = create("v1/vehicles", vehicle("v1 van"));
        long deliveryId = create("v1/deliveries", delivery(id));
        long scenarioId = create("v1/scenarios", scenario("v1 scenario", vehicleId, deliveryId));
        for (String resource : new String[]{"vehicles", "deliveries", "scenarios"}) {
            mvc.perform(get("/api/v1/" + resource)).andExpect(status().isOk());
            mvc.perform(get("/api/v2/" + resource)).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/v1/scenarios/" + scenarioId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/deliveries/" + deliveryId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/vehicles/" + vehicleId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/customers/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/v3/api-docs/v1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{id}/deliveries']").exists())
                .andExpect(jsonPath("$.paths['/api/customers']").doesNotExist());
        mvc.perform(get("/v3/api-docs/legacy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers']").doesNotExist());
    }
}
