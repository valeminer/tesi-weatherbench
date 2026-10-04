package it.tesis.weatherbench.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller interface per le dimostrazioni del Capitolo 2 (persistence context vs modello stateless).
 */
@Tag(name = "Lifecycle", description = "Ciclo di vita delle entita' JPA e modello operativo MongoDB")
@RequestMapping("/api/lifecycle")
public interface LifecycleDemoController {

    @Operation(summary = "NEW/MANAGED/DETACHED, first-level cache, dirty checking, merge, proxy LAZY")
    @GetMapping("/jpa/persistence-context")
    ResponseEntity<Map<String, Object>> jpaPersistenceContext();

    @Operation(summary = "Mapping diretto POJO/BSON senza tracking, identity map o proxy")
    @GetMapping("/mongo/stateless")
    ResponseEntity<Map<String, Object>> mongoStateless();
}
