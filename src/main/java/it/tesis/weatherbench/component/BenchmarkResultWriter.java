package it.tesis.weatherbench.component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.benchmark.BenchmarkReportDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * Accoda ogni report al file CSV dei risultati, pronto per Excel/pandas/LaTeX (pgfplotstable).
 */
@Slf4j
@Component
public class BenchmarkResultWriter {

    private final Path resultsFile;

    public BenchmarkResultWriter(BenchmarkProperties properties) {
        this.resultsFile = properties.getResultsFile().toAbsolutePath();
    }

    public synchronized void append(BenchmarkReportDTO report) {
        try {
            if (resultsFile.getParent() != null) {
                Files.createDirectories(resultsFile.getParent());
            }
            if (Files.notExists(resultsFile) || Files.size(resultsFile) == 0) {
                Files.writeString(resultsFile, BenchmarkReportDTO.csvHeader() + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            Files.writeString(resultsFile, report.toCsvRow() + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Unable to append benchmark report {} to {}: {}", report.getRunId(), resultsFile, e.getMessage());
        }
    }

    public Path getResultsFile() {
        return resultsFile;
    }
}
