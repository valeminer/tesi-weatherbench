package it.tesis.weatherbench.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.tesis.weatherbench.dto.benchmark.BenchmarkReportDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.enumeration.WriteMode;
import it.tesis.weatherbench.service.BenchmarkService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class BenchmarkControllerImpl implements BenchmarkController {

    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

    private final BenchmarkService benchmarkService;

    public BenchmarkControllerImpl(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<BenchmarkReportDTO> writeBatch(StorageEngineType engine, int records, WriteMode mode,
            Integer chunkSize, boolean purgeBefore) {
        log.info("Benchmark write requested: engine={}, records={}, mode={}, chunkSize={}, purgeBefore={}",
                engine, records, mode, chunkSize, purgeBefore);
        return ResponseEntity.ok(benchmarkService.writeBatch(engine, records, mode, chunkSize, purgeBefore));
    }

    @Override
    public ResponseEntity<BenchmarkReportDTO> readRange(StorageEngineType engine, int datasetSize, int iterations,
            int windowHours, int warmup) {
        log.info("Benchmark read-range requested: engine={}, datasetSize={}, iterations={}, windowHours={}",
                engine, datasetSize, iterations, windowHours);
        return ResponseEntity.ok(benchmarkService.readRange(engine, datasetSize, iterations, windowHours, warmup));
    }

    @Override
    public ResponseEntity<BenchmarkReportDTO> aggregate(StorageEngineType engine, int datasetSize, int iterations,
            int warmup) {
        log.info("Benchmark aggregate requested: engine={}, datasetSize={}, iterations={}", engine, datasetSize, iterations);
        return ResponseEntity.ok(benchmarkService.aggregate(engine, datasetSize, iterations, warmup));
    }

    @Override
    public ResponseEntity<BenchmarkReportDTO> readById(StorageEngineType engine, int datasetSize, int iterations,
            int warmup) {
        log.info("Benchmark read-by-id requested: engine={}, datasetSize={}, iterations={}", engine, datasetSize, iterations);
        return ResponseEntity.ok(benchmarkService.readById(engine, datasetSize, iterations, warmup));
    }

    @Override
    public ResponseEntity<Map<String, Object>> count(StorageEngineType engine) {
        return ResponseEntity.ok(Map.of("engine", engine, "records", benchmarkService.count(engine)));
    }

    @Override
    public ResponseEntity<Void> purge(StorageEngineType engine) {
        benchmarkService.purge(engine);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Map<String, Object>> datasetPreview(int records, int limit) {
        return ResponseEntity.ok(benchmarkService.datasetPreview(records, limit));
    }

    @Override
    public ResponseEntity<List<BenchmarkReportDTO>> reports() {
        return ResponseEntity.ok(benchmarkService.reports());
    }

    @Override
    public ResponseEntity<String> reportsCsv() {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header("Content-Disposition", "attachment; filename=benchmark-results.csv")
                .body(benchmarkService.reportsAsCsv());
    }

    @Override
    public ResponseEntity<Void> clearReports() {
        benchmarkService.clearReports();
        return ResponseEntity.noContent().build();
    }
}
