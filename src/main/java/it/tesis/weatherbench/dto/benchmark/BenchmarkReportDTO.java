package it.tesis.weatherbench.dto.benchmark;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import it.tesis.weatherbench.enumeration.BenchmarkOperation;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Report di una singola esecuzione di benchmark (Capitolo 4). Serializzato in JSON come risposta
 * REST e in una riga CSV nel file {@code logs/benchmark-results.csv}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkReportDTO {

    private static final String CSV_SEPARATOR = ",";

    private String runId;
    private StorageEngineType engine;
    private BenchmarkOperation operation;
    private Instant startedAt;
    private Instant finishedAt;

    /** Parametri effettivi dell'esecuzione (replicabilita') */
    private Map<String, Object> parameters;
    private DatasetDescriptorDTO dataset;

    /** Operazioni logiche usate per il throughput: record scritti (write) o query eseguite (read) */
    private long operations;
    /** Record scritti o restituiti dalle query */
    private long records;
    /** Record presenti nello storage al termine (solo write) */
    private Long storedRecords;

    private double totalTimeMs;
    private double throughputOpsPerSec;

    private LatencyStats latency;
    private MemoryStats memory;
    private GcStats gc;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyStats {
        /** Numero di campioni: una chiamata alla porta (saveBatch per blocco, saveSingle, query) */
        private long samples;
        private double meanMs;
        private double p50Ms;
        private double p95Ms;
        private double p99Ms;
        private double maxMs;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemoryStats {
        /** Runtime.totalMemory() - freeMemory() */
        private double heapUsedBeforeMb;
        private double heapUsedAfterMb;
        /** Differenza grezza: oggetti vivi + garbage non ancora raccolto */
        private double heapDeltaMb;
        /** Actuator jvm.memory.used{area=heap} */
        private double actuatorHeapUsedBeforeMb;
        private double actuatorHeapUsedAfterMb;
        private double actuatorHeapDeltaMb;
        /** Somma dei picchi dei memory pool heap durante l'esecuzione */
        private double heapPeakMb;
        /** Heap dopo una full GC a fine run: memoria effettivamente trattenuta */
        private Double heapRetainedAfterGcMb;
        private Double heapRetainedDeltaMb;
        private double heapCommittedMb;
        private double heapMaxMb;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GcStats {
        /** Delta di jvm.gc.pause (Micrometer): pause stop-the-world */
        private long pauseCount;
        private double pauseTimeMs;
        /** Delta di GarbageCollectorMXBean: include i cicli concorrenti */
        private long collectionCount;
        private long collectionTimeMs;
        /** collectionTimeMs / totalTimeMs */
        private double gcTimePercent;
        private List<String> collectors;
    }

    public static String csvHeader() {
        return String.join(CSV_SEPARATOR, "runId", "startedAt", "engine", "operation", "mode", "records", "operations",
                "latencySamples", "totalTimeMs", "throughputOpsPerSec", "latencyMeanMs", "latencyP50Ms", "latencyP95Ms",
                "latencyP99Ms", "latencyMaxMs", "heapBeforeMb", "heapAfterMb", "heapDeltaMb", "actuatorHeapDeltaMb",
                "heapPeakMb", "heapRetainedDeltaMb", "gcPauseCount", "gcPauseTimeMs", "gcCollectionCount",
                "gcCollectionTimeMs", "gcTimePercent", "seed", "datasetRecords", "chunkSize", "heapMaxMb", "collectors",
                "fingerprint");
    }

    public String toCsvRow() {
        return Stream.of(runId, startedAt, engine, operation, parameter("mode"), records, operations, latency.getSamples(),
                        num(totalTimeMs), num(throughputOpsPerSec), num(latency.getMeanMs()), num(latency.getP50Ms()),
                        num(latency.getP95Ms()), num(latency.getP99Ms()), num(latency.getMaxMs()),
                        num(memory.getHeapUsedBeforeMb()), num(memory.getHeapUsedAfterMb()), num(memory.getHeapDeltaMb()),
                        num(memory.getActuatorHeapDeltaMb()), num(memory.getHeapPeakMb()),
                        memory.getHeapRetainedDeltaMb() == null ? "" : num(memory.getHeapRetainedDeltaMb()),
                        gc.getPauseCount(), num(gc.getPauseTimeMs()), gc.getCollectionCount(), gc.getCollectionTimeMs(),
                        num(gc.getGcTimePercent()), parameter("seed"), parameter("datasetRecords"), parameter("chunkSize"),
                        num(memory.getHeapMaxMb()), String.join("|", gc.getCollectors()),
                        dataset == null || dataset.getFingerprint() == null ? "" : dataset.getFingerprint())
                .map(value -> Objects.toString(value, ""))
                .collect(Collectors.joining(CSV_SEPARATOR));
    }

    public String toLogLine() {
        return String.format(Locale.ROOT,
                "BENCHMARK %s engine=%s op=%s ops=%d records=%d time=%.1fms throughput=%.1f ops/s "
                        + "latency[mean=%.3f p95=%.3f p99=%.3f max=%.3f ms, n=%d] heap[delta=%.1f peak=%.1f MB] "
                        + "gc[pauses=%d %.1fms, collections=%d %dms, %.2f%%]",
                runId, engine, operation, operations, records, totalTimeMs, throughputOpsPerSec,
                latency.getMeanMs(), latency.getP95Ms(), latency.getP99Ms(), latency.getMaxMs(), latency.getSamples(),
                memory.getHeapDeltaMb(), memory.getHeapPeakMb(), gc.getPauseCount(), gc.getPauseTimeMs(),
                gc.getCollectionCount(), gc.getCollectionTimeMs(), gc.getGcTimePercent());
    }

    private Object parameter(String key) {
        return parameters == null ? null : parameters.get(key);
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
