package it.tesis.weatherbench.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import it.tesis.weatherbench.dto.benchmark.BenchmarkReportDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.enumeration.WriteMode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Controller interface per l'esecuzione dei benchmark. {engine} = jpa | mongo (case-insensitive).
 */
@Tag(name = "Benchmark", description = "Throughput, latenza, heap e GC per motore di persistenza")
@RequestMapping("/api/benchmark")
public interface BenchmarkController {

    @Operation(summary = "Scrive un dataset sintetico deterministico di N record")
    @PostMapping("/{engine}/write-batch")
    ResponseEntity<BenchmarkReportDTO> writeBatch(
            @PathVariable StorageEngineType engine,
            @Parameter(description = "Numero di record (1.000, 10.000, 100.000, 500.000...)")
            @RequestParam(defaultValue = "10000") @Min(1) @Max(1_000_000) int records,
            @RequestParam(defaultValue = "BATCH") WriteMode mode,
            @Parameter(description = "Record per chiamata a saveBatch (default weatherbench.benchmark.chunk-size)")
            @RequestParam(required = false) @Min(1) Integer chunkSize,
            @Parameter(description = "Svuota lo storage prima della misura")
            @RequestParam(defaultValue = "true") boolean purgeBefore);

    @Operation(summary = "Query su intervallo temporale [start, start + windowHours) per stazione")
    @GetMapping("/{engine}/read-range")
    ResponseEntity<BenchmarkReportDTO> readRange(
            @PathVariable StorageEngineType engine,
            @Parameter(description = "Dimensione del dataset precedentemente scritto")
            @RequestParam(defaultValue = "10000") @Min(1) @Max(1_000_000) int datasetSize,
            @RequestParam(defaultValue = "200") @Min(1) @Max(100_000) int iterations,
            @RequestParam(defaultValue = "24") @Min(1) @Max(24 * 366) int windowHours,
            @RequestParam(defaultValue = "20") @Min(0) @Max(10_000) int warmup);

    @Operation(summary = "Statistiche giornaliere (count, avg, min, max, sum) per stazione e giorno")
    @GetMapping("/{engine}/aggregate")
    ResponseEntity<BenchmarkReportDTO> aggregate(
            @PathVariable StorageEngineType engine,
            @RequestParam(defaultValue = "10000") @Min(1) @Max(1_000_000) int datasetSize,
            @RequestParam(defaultValue = "200") @Min(1) @Max(100_000) int iterations,
            @RequestParam(defaultValue = "20") @Min(0) @Max(10_000) int warmup);

    @Operation(summary = "Lookup puntuale per id")
    @GetMapping("/{engine}/read-by-id")
    ResponseEntity<BenchmarkReportDTO> readById(
            @PathVariable StorageEngineType engine,
            @RequestParam(defaultValue = "10000") @Min(1) @Max(1_000_000) int datasetSize,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(1_000_000) int iterations,
            @RequestParam(defaultValue = "100") @Min(0) @Max(100_000) int warmup);

    @Operation(summary = "Numero di misure memorizzate")
    @GetMapping("/{engine}/count")
    ResponseEntity<Map<String, Object>> count(@PathVariable StorageEngineType engine);

    @Operation(summary = "Svuota lo storage del motore")
    @DeleteMapping("/{engine}/data")
    ResponseEntity<Void> purge(@PathVariable StorageEngineType engine);

    @Operation(summary = "Descrittore, fingerprint e primi record del dataset sintetico")
    @GetMapping("/dataset")
    ResponseEntity<Map<String, Object>> datasetPreview(
            @RequestParam(defaultValue = "1000") @Min(1) @Max(1_000_000) int records,
            @RequestParam(defaultValue = "10") @Min(0) @Max(1000) int limit);

    @Operation(summary = "Storico dei report della sessione")
    @GetMapping("/reports")
    ResponseEntity<List<BenchmarkReportDTO>> reports();

    @Operation(summary = "Storico dei report della sessione in formato CSV")
    @GetMapping(value = "/reports/csv", produces = "text/csv")
    ResponseEntity<String> reportsCsv();

    @DeleteMapping("/reports")
    ResponseEntity<Void> clearReports();
}
