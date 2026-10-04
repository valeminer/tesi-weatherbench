package it.tesis.weatherbench.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.tesis.weatherbench.dto.ingestion.IngestionResponseDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Controller interface per l'ingestione di dati reali da Open-Meteo.
 */
@Tag(name = "Ingestion", description = "Campioni reali dalle API pubbliche Open-Meteo")
@RequestMapping("/api/ingestion")
public interface IngestionController {

    @Operation(summary = "Scarica le serie orarie degli ultimi N giorni e le salva con saveBatch()")
    @PostMapping("/{engine}/open-meteo")
    ResponseEntity<IngestionResponseDTO> ingestOpenMeteo(
            @PathVariable StorageEngineType engine,
            @RequestParam @Pattern(regexp = "^[A-Za-z0-9-]{1,32}$") String stationCode,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue = "7") @Min(1) @Max(92) int pastDays);
}
