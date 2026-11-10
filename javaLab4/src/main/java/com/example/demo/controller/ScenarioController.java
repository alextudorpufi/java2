package com.example.demo.controller;

import com.example.demo.dto.ScenarioRequest;
import com.example.demo.dto.ScenarioResponse;
import com.example.demo.service.ScenarioService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping(value = {"/api/v1/scenarios", "/api/scenarios"}, produces = {"application/json", "application/xml"})
@RequiredArgsConstructor
public class ScenarioController {
    private final ScenarioService service;

    @ApiResponse(responseCode = "201", description = "Created")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = {"application/json", "application/xml"})
    public ResponseEntity<ScenarioResponse> create(@Valid @RequestBody ScenarioRequest request) {
        var created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<ScenarioResponse> findAll() {
        return service.listResponses();
    }

    @Operation(
            summary = "Read a scenario",
            description = "Returns selected vehicle/delivery IDs. Choose JSON or XML with Accept. "
                    + "Send a previous ETag in If-None-Match to receive 304 when unchanged.",
            parameters = @Parameter(name = "If-None-Match", in = ParameterIn.HEADER,
                    description = "A previously returned ETag, a list of ETags, or *"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scenario found",
                    headers = @Header(name = "ETag", description = "Weak validator for this representation",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "304", description = "Unchanged; no response body", content = @Content,
                    headers = @Header(name = "ETag", schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "404", description = "Scenario not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ScenarioResponse findById(@PathVariable Long id) {
        return service.response(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/{id}", consumes = {"application/json", "application/xml"})
    public ScenarioResponse update(@PathVariable Long id, @Valid @RequestBody ScenarioRequest request) {
        return service.update(id, request);
    }

    @ApiResponse(responseCode = "204", description = "Deleted; no response body")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
