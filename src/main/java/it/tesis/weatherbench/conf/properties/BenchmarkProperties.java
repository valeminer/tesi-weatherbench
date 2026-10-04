package it.tesis.weatherbench.conf.properties;

import java.nio.file.Path;
import java.time.Instant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Parametri sperimentali del benchmark (prefisso {@code weatherbench.benchmark}).
 * Tutti i valori sono riportati nel report JSON per garantire la replicabilita' dell'esperimento.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "weatherbench.benchmark")
public class BenchmarkProperties {

    private long seed = 42L;

    @Min(1)
    private int stations = 50;

    @Min(1)
    private int samplingIntervalMinutes = 10;

    @NotNull
    private Instant startInstant = Instant.parse("2024-01-01T00:00:00Z");

    @Min(1)
    private int chunkSize = 5000;

    @Min(1)
    private int jpaFlushInterval = 500;

    @Min(1)
    private int mongoBulkSize = 1000;

    private boolean gcBeforeRun = true;

    @Min(1)
    private int maxRecords = 1_000_000;

    @Min(1)
    private int reportHistorySize = 1000;

    @NotNull
    private Path resultsFile = Path.of("logs", "benchmark-results.csv");
}
