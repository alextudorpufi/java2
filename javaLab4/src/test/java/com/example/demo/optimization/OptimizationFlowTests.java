package com.example.demo.optimization;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.messaging.OptimizationMessages.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:optimization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class OptimizationFlowTests {
    @Autowired MockMvc mvc;
    @Autowired ScenarioRepository scenarios;
    @Autowired DepotRepository depots;
    @Autowired VehicleRepository vehicles;
    @Autowired OptimizationJobRepository jobs;
    @Autowired OptimizationPublisher publisher;
    @Autowired OptimizationService service;
    @MockitoBean KafkaTemplate<String, String> kafka;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void durableSnapshotPublicationRetryAndIdempotentAcknowledgment() throws Exception {
        var depot = depots.save(new Depot("Job depot", BigDecimal.ZERO, BigDecimal.ZERO));
        var available = vehicles.save(new com.example.demo.entity.Vehicle("Available", BigDecimal.TEN, depot));
        var unavailable = new com.example.demo.entity.Vehicle("Unavailable", BigDecimal.TEN, depot);
        unavailable.setAvailable(false);
        vehicles.save(unavailable);
        var scenario = new Scenario();
        scenario.setName("Snapshot");
        scenario.replaceMembership(Set.of(available, unavailable), Set.of());
        long scenarioId = scenarios.save(scenario).getId();
        var response = mvc.perform(post("/api/v1/scenarios/" + scenarioId + "/optimize"))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED")).andReturn();
        UUID id = UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("requestId").asText());
        mvc.perform(get("/api/v1/optimizations/" + id + "/solution")).andExpect(status().isConflict());
        var snapshot = json.readValue(jobs.findById(id).orElseThrow().requestJson, Request.class);
        assertEquals(1, snapshot.vehicles().size());
        assertEquals(available.getId(), snapshot.vehicles().getFirst().id());
        assertFalse(jobs.findById(id).orElseThrow().published);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new RuntimeException("offline")));
        assertThrows(Exception.class, () -> publisher.publish(id));
        assertFalse(jobs.findById(id).orElseThrow().published);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish(id);
        assertTrue(jobs.findById(id).orElseThrow().published);
        verify(kafka, times(2)).send(eq("vrp.optimization.requests.v1"), eq(id.toString()), eq(json.writeValueAsString(snapshot)));
        var ack = new Acknowledgment(1, id, scenarioId, Status.COMPLETED, List.of(), List.of(), "Done");
        service.acknowledge(json.writeValueAsString(ack));
        service.acknowledge(json.writeValueAsString(new Acknowledgment(1, id, scenarioId, Status.FAILED,
                List.of(), List.of(), "Duplicate")));
        mvc.perform(get("/api/v1/optimizations/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.acknowledgment.message").value("Done"));
        mvc.perform(get("/api/v1/optimizations/" + id + "/solution")).andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(id.toString()))
                .andExpect(jsonPath("$.routes").isArray());
        service.handleDeadLetter(new ConsumerRecord<>("vrp.optimization.requests.v1.DLT", 0, 1,
                id.toString(), "{broken"));
        assertEquals("COMPLETED", service.find(id).status());

        var second = service.submit(scenarioId);
        service.handleDeadLetter(new ConsumerRecord<>("vrp.optimization.requests.v1.DLT", 0, 2,
                second.requestId().toString(), "{broken"));
        mvc.perform(get("/api/v1/optimizations/" + second.requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(get("/api/v1/optimizations/" + second.requestId() + "/solution"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/optimizations/" + UUID.randomUUID() + "/solution"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/scenarios/999999/optimize")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/optimizations/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test @WithMockUser(roles = "DRIVER") void driversCannotSubmitOrReadJobs() throws Exception {
        mvc.perform(post("/api/v1/scenarios/1/optimize")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/optimizations/" + UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/optimizations/" + UUID.randomUUID() + "/solution")).andExpect(status().isForbidden());
    }
}
