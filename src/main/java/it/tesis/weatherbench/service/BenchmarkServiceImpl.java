package it.tesis.weatherbench.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import it.tesis.weatherbench.component.BenchmarkResultWriter;
import it.tesis.weatherbench.component.JvmMetricsCollector;
import it.tesis.weatherbench.component.JvmMetricsCollector.JvmSnapshot;
import it.tesis.weatherbench.component.StorageEngineResolver;
import it.tesis.weatherbench.component.SyntheticDatasetGenerator;
import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.benchmark.BenchmarkReportDTO;
import it.tesis.weatherbench.dto.benchmark.DatasetDescriptorDTO;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.enumeration.BenchmarkOperation;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.enumeration.WriteMode;
import it.tesis.weatherbench.exception.BenchmarkAlreadyRunningException;
import it.tesis.weatherbench.exception.WrongInputParameterException;
import it.tesis.weatherbench.port.StorageEnginePort;
import it.tesis.weatherbench.utils.Constants;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrazione dei benchmark. Protocollo di misura comune a tutte le operazioni:
 * <ol>
 *     <li>preparazione fuori misura (generazione dataset, purge, warm-up JIT);</li>
 *     <li>full GC opzionale + reset dei picchi dei memory pool;</li>
 *     <li>snapshot JVM "before";</li>
 *     <li>esecuzione: ogni chiamata alla porta e' cronometrata da un Micrometer {@link Timer}
 *     dedicato al run (percentili p50/p95/p99);</li>
 *     <li>snapshot JVM "after" e picco heap; full GC opzionale per misurare la memoria trattenuta;</li>
 *     <li>costruzione del report, log sintetico, append su CSV, storico in memoria.</li>
 * </ol>
 * Un lock garantisce che due benchmark non si sovrappongano (le misure JVM sono globali al processo).
 */
@Slf4j
@Service
public class BenchmarkServiceImpl implements BenchmarkService {

    private static final double[] PERCENTILES = {0.5, 0.95, 0.99};
    private static final long QUERY_PLAN_SEED_OFFSET = 1L;

    private final StorageEngineResolver engineResolver;
    private final SyntheticDatasetGenerator datasetGenerator;
    private final JvmMetricsCollector jvmMetricsCollector;
    private final BenchmarkResultWriter resultWriter;
    private final MeterRegistry meterRegistry;
    private final BenchmarkProperties properties;

    private final ReentrantLock benchmarkLock = new ReentrantLock();
    private final Deque<BenchmarkReportDTO> history = new ConcurrentLinkedDeque<>();

    public BenchmarkServiceImpl(StorageEngineResolver engineResolver, SyntheticDatasetGenerator datasetGenerator,
            JvmMetricsCollector jvmMetricsCollector, BenchmarkResultWriter resultWriter, MeterRegistry meterRegistry,
            BenchmarkProperties properties) {
        this.engineResolver = engineResolver;
        this.datasetGenerator = datasetGenerator;
        this.jvmMetricsCollector = jvmMetricsCollector;
        this.resultWriter = resultWriter;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @Override
    public BenchmarkReportDTO writeBatch(StorageEngineType engine, int records, WriteMode mode, Integer chunkSize,
            boolean purgeBefore) {
        validateRecords(records);
        int effectiveChunkSize = chunkSize != null ? chunkSize : properties.getChunkSize();
        BenchmarkOperation operation = mode == WriteMode.SINGLE ? BenchmarkOperation.WRITE_SINGLE : BenchmarkOperation.WRITE_BATCH;

        return exclusively(() -> {
            StorageEnginePort port = engineResolver.resolve(engine);
            if (purgeBefore) {
                port.purgeAll();
            }
            // Dataset generato prima della misura: e' presente nell'heap sia nello snapshot before che after
            List<MeasurementDto> dataset = datasetGenerator.generate(records);
            DatasetDescriptorDTO descriptor = datasetGenerator.describe(records, dataset);

            Map<String, Object> parameters = baseParameters(records);
            parameters.put("mode", mode);
            parameters.put("chunkSize", mode == WriteMode.BATCH ? effectiveChunkSize : 1);
            parameters.put("purgeBefore", purgeBefore);
            parameters.put("jpaFlushInterval", properties.getJpaFlushInterval());
            parameters.put("mongoBulkSize", properties.getMongoBulkSize());

            BenchmarkReportDTO report = measure(port, operation, parameters, null, timer -> {
                if (mode == WriteMode.SINGLE) {
                    dataset.forEach(dto -> timer.record(() -> port.saveSingle(dto)));
                } else {
                    for (int from = 0; from < dataset.size(); from += effectiveChunkSize) {
                        List<MeasurementDto> chunk = dataset.subList(from, Math.min(from + effectiveChunkSize, dataset.size()));
                        timer.record(() -> port.saveBatch(chunk));
                    }
                }
                return new Outcome(records, records);
            });
            report.setDataset(descriptor);
            report.setStoredRecords(port.count());
            return publish(report);
        });
    }

    @Override
    public BenchmarkReportDTO readRange(StorageEngineType engine, int datasetSize, int iterations, int windowHours,
            int warmup) {
        validateRecords(datasetSize);
        DatasetDescriptorDTO descriptor = datasetGenerator.describe(datasetSize);
        Duration window = Duration.ofHours(windowHours);
        List<RangeQuery> plan = rangeQueryPlan(descriptor, window, warmup + iterations);

        return exclusively(() -> {
            StorageEnginePort port = engineResolver.resolve(engine);
            Map<String, Object> parameters = readParameters(datasetSize, iterations, warmup);
            parameters.put("windowHours", windowHours);

            BenchmarkReportDTO report = measure(port, BenchmarkOperation.READ_RANGE, parameters,
                    () -> plan.subList(0, warmup).forEach(q -> port.findRange(q.stationCode(), q.start(), q.end())),
                    timer -> {
                        long rows = 0;
                        for (RangeQuery q : plan.subList(warmup, plan.size())) {
                            rows += timer.record(() -> port.findRange(q.stationCode(), q.start(), q.end())).size();
                        }
                        return new Outcome(iterations, rows);
                    });
            report.setDataset(descriptor);
            return publish(report);
        });
    }

    @Override
    public BenchmarkReportDTO aggregate(StorageEngineType engine, int datasetSize, int iterations, int warmup) {
        validateRecords(datasetSize);
        DatasetDescriptorDTO descriptor = datasetGenerator.describe(datasetSize);
        List<DailyQuery> plan = dailyQueryPlan(descriptor, warmup + iterations);

        return exclusively(() -> {
            StorageEnginePort port = engineResolver.resolve(engine);
            BenchmarkReportDTO report = measure(port, BenchmarkOperation.AGGREGATE_DAILY,
                    readParameters(datasetSize, iterations, warmup),
                    () -> plan.subList(0, warmup).forEach(q -> port.computeDailyStats(q.stationCode(), q.date())),
                    timer -> {
                        long samples = 0;
                        for (DailyQuery q : plan.subList(warmup, plan.size())) {
                            samples += timer.record(() -> port.computeDailyStats(q.stationCode(), q.date())).getSampleCount();
                        }
                        return new Outcome(iterations, samples);
                    });
            report.setDataset(descriptor);
            return publish(report);
        });
    }

    @Override
    public BenchmarkReportDTO readById(StorageEngineType engine, int datasetSize, int iterations, int warmup) {
        validateRecords(datasetSize);
        DatasetDescriptorDTO descriptor = datasetGenerator.describe(datasetSize);
        RandomGenerator random = queryPlanRandom();
        List<String> plan = new ArrayList<>(warmup + iterations);
        for (int i = 0; i < warmup + iterations; i++) {
            plan.add(datasetGenerator.measurementId(random.nextInt(datasetSize)));
        }

        return exclusively(() -> {
            StorageEnginePort port = engineResolver.resolve(engine);
            Map<String, Object> parameters = readParameters(datasetSize, iterations, warmup);

            BenchmarkReportDTO report = measure(port, BenchmarkOperation.READ_BY_ID, parameters,
                    () -> plan.subList(0, warmup).forEach(port::findById),
                    timer -> {
                        long found = 0;
                        for (String id : plan.subList(warmup, plan.size())) {
                            if (timer.record(() -> port.findById(id)) != null) {
                                found++;
                            }
                        }
                        return new Outcome(iterations, found);
                    });
            report.setDataset(descriptor);
            report.getParameters().put("notFound", iterations - report.getRecords());
            return publish(report);
        });
    }

    @Override
    public Map<String, Object> datasetPreview(int records, int limit) {
        validateRecords(records);
        List<MeasurementDto> dataset = datasetGenerator.generate(records);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("dataset", datasetGenerator.describe(records, dataset));
        preview.put("sample", dataset.subList(0, Math.min(limit, dataset.size())));
        return preview;
    }

    @Override
    public long count(StorageEngineType engine) {
        return engineResolver.resolve(engine).count();
    }

    @Override
    public void purge(StorageEngineType engine) {
        exclusively(() -> {
            engineResolver.resolve(engine).purgeAll();
            return null;
        });
    }

    @Override
    public List<BenchmarkReportDTO> reports() {
        return List.copyOf(history);
    }

    @Override
    public String reportsAsCsv() {
        return history.stream().map(BenchmarkReportDTO::toCsvRow)
                .collect(Collectors.joining("\n", BenchmarkReportDTO.csvHeader() + "\n", "\n"));
    }

    @Override
    public void clearReports() {
        history.clear();
    }

    private BenchmarkReportDTO measure(StorageEnginePort port, BenchmarkOperation operation,
            Map<String, Object> parameters, Runnable warmup, MeasuredBody body) {
        String runId = operation + "-" + port.engineType() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Timer timer = Timer.builder(Constants.METRIC_BENCHMARK_LATENCY)
                .description("Latency of a single StorageEnginePort call during a benchmark run")
                .tag(Constants.METRIC_TAG_ENGINE, port.engineType().name())
                .tag(Constants.METRIC_TAG_OPERATION, operation.name())
                .tag(Constants.METRIC_TAG_RUN_ID, runId)
                .publishPercentiles(PERCENTILES)
                .percentilePrecision(3)
                // Finestra unica lunga quanto il run: i percentili coprono tutti i campioni, non solo gli ultimi minuti
                .distributionStatisticExpiry(Duration.ofDays(1))
                .distributionStatisticBufferLength(1)
                .register(meterRegistry);
        try {
            if (warmup != null) {
                warmup.run();
            }
            if (properties.isGcBeforeRun()) {
                jvmMetricsCollector.requestGc();
            }
            jvmMetricsCollector.resetPeakHeapUsage();

            Instant startedAt = Instant.now();
            JvmSnapshot before = jvmMetricsCollector.capture();
            Outcome outcome = body.run(timer);
            JvmSnapshot after = jvmMetricsCollector.capture();
            long peakHeap = jvmMetricsCollector.peakHeapUsedBytes();
            Instant finishedAt = Instant.now();

            Long retainedHeap = null;
            if (properties.isGcBeforeRun()) {
                jvmMetricsCollector.requestGc();
                retainedHeap = jvmMetricsCollector.capture().mxBeanHeapUsedBytes();
            }

            return buildReport(runId, port.engineType(), operation, parameters, startedAt, finishedAt, outcome,
                    timer.takeSnapshot(), before, after, peakHeap, retainedHeap);
        } finally {
            meterRegistry.remove(timer);
        }
    }

    private BenchmarkReportDTO buildReport(String runId, StorageEngineType engine, BenchmarkOperation operation,
            Map<String, Object> parameters, Instant startedAt, Instant finishedAt, Outcome outcome,
            HistogramSnapshot latency, JvmSnapshot before, JvmSnapshot after, long peakHeap, Long retainedHeap) {
        double totalTimeMs = (after.nanoTime() - before.nanoTime()) / 1_000_000d;
        long collectionTimeMs = after.gcCollectionMillis() - before.gcCollectionMillis();

        return BenchmarkReportDTO.builder()
                .runId(runId)
                .engine(engine)
                .operation(operation)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .parameters(parameters)
                .operations(outcome.operations())
                .records(outcome.records())
                .totalTimeMs(totalTimeMs)
                .throughputOpsPerSec(totalTimeMs > 0 ? outcome.operations() / (totalTimeMs / 1000d) : 0)
                .latency(BenchmarkReportDTO.LatencyStats.builder()
                        .samples(latency.count())
                        .meanMs(latency.mean(TimeUnit.MILLISECONDS))
                        .p50Ms(percentile(latency, 0.5))
                        .p95Ms(percentile(latency, 0.95))
                        .p99Ms(percentile(latency, 0.99))
                        .maxMs(latency.max(TimeUnit.MILLISECONDS))
                        .build())
                .memory(BenchmarkReportDTO.MemoryStats.builder()
                        .heapUsedBeforeMb(mb(before.runtimeHeapUsedBytes()))
                        .heapUsedAfterMb(mb(after.runtimeHeapUsedBytes()))
                        .heapDeltaMb(mb(after.runtimeHeapUsedBytes() - before.runtimeHeapUsedBytes()))
                        .actuatorHeapUsedBeforeMb(mb(before.actuatorHeapUsedBytes()))
                        .actuatorHeapUsedAfterMb(mb(after.actuatorHeapUsedBytes()))
                        .actuatorHeapDeltaMb(mb(after.actuatorHeapUsedBytes() - before.actuatorHeapUsedBytes()))
                        .heapPeakMb(mb(peakHeap))
                        .heapRetainedAfterGcMb(retainedHeap == null ? null : mb(retainedHeap))
                        .heapRetainedDeltaMb(retainedHeap == null ? null : mb(retainedHeap - before.mxBeanHeapUsedBytes()))
                        .heapCommittedMb(mb(after.heapCommittedBytes()))
                        .heapMaxMb(mb(after.heapMaxBytes()))
                        .build())
                .gc(BenchmarkReportDTO.GcStats.builder()
                        .pauseCount(after.gcPauseCount() - before.gcPauseCount())
                        .pauseTimeMs(after.gcPauseMillis() - before.gcPauseMillis())
                        .collectionCount(after.gcCollectionCount() - before.gcCollectionCount())
                        .collectionTimeMs(collectionTimeMs)
                        .gcTimePercent(totalTimeMs > 0 ? collectionTimeMs * 100d / totalTimeMs : 0)
                        .collectors(jvmMetricsCollector.garbageCollectorNames())
                        .build())
                .build();
    }

    private BenchmarkReportDTO publish(BenchmarkReportDTO report) {
        log.info(report.toLogLine());
        resultWriter.append(report);
        history.addLast(report);
        while (history.size() > properties.getReportHistorySize()) {
            history.pollFirst();
        }
        return report;
    }

    private <T> T exclusively(Supplier<T> action) {
        if (!benchmarkLock.tryLock()) {
            throw new BenchmarkAlreadyRunningException("Another benchmark is running: runs are serialized to keep JVM measurements isolated");
        }
        try {
            return action.get();
        } finally {
            benchmarkLock.unlock();
        }
    }

    /**
     * Piano di query deterministico (seed derivato): JPA e MongoDB eseguono esattamente la stessa
     * sequenza di interrogazioni.
     */
    private List<RangeQuery> rangeQueryPlan(DatasetDescriptorDTO descriptor, Duration window, int size) {
        RandomGenerator random = queryPlanRandom();
        long spanSeconds = Math.max(0, Duration.between(descriptor.getFirstTimestamp(), descriptor.getLastTimestamp())
                .minus(window).getSeconds());
        List<RangeQuery> plan = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Instant start = descriptor.getFirstTimestamp().plusSeconds(spanSeconds > 0 ? random.nextLong(spanSeconds + 1) : 0)
                    .truncatedTo(ChronoUnit.MINUTES);
            plan.add(new RangeQuery(randomStation(random, descriptor), start, start.plus(window)));
        }
        return plan;
    }

    private List<DailyQuery> dailyQueryPlan(DatasetDescriptorDTO descriptor, int size) {
        RandomGenerator random = queryPlanRandom();
        LocalDate firstDay = LocalDate.ofInstant(descriptor.getFirstTimestamp(), ZoneOffset.UTC);
        long days = ChronoUnit.DAYS.between(firstDay, LocalDate.ofInstant(descriptor.getLastTimestamp(), ZoneOffset.UTC)) + 1;
        List<DailyQuery> plan = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            plan.add(new DailyQuery(randomStation(random, descriptor), firstDay.plusDays(random.nextLong(days))));
        }
        return plan;
    }

    private RandomGenerator queryPlanRandom() {
        return new SplittableRandom(properties.getSeed() + QUERY_PLAN_SEED_OFFSET);
    }

    private static String randomStation(RandomGenerator random, DatasetDescriptorDTO descriptor) {
        return SyntheticDatasetGenerator.stationCode(random.nextInt(descriptor.getStations()));
    }

    private Map<String, Object> baseParameters(int datasetRecords) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("datasetRecords", datasetRecords);
        parameters.put("seed", properties.getSeed());
        parameters.put("stations", properties.getStations());
        parameters.put("samplingIntervalMinutes", properties.getSamplingIntervalMinutes());
        parameters.put("gcBeforeRun", properties.isGcBeforeRun());
        return parameters;
    }

    private Map<String, Object> readParameters(int datasetSize, int iterations, int warmup) {
        Map<String, Object> parameters = baseParameters(datasetSize);
        parameters.put("iterations", iterations);
        parameters.put("warmup", warmup);
        return parameters;
    }

    private void validateRecords(int records) {
        if (records < 1 || records > properties.getMaxRecords()) {
            throw new WrongInputParameterException("records/datasetSize must be between 1 and " + properties.getMaxRecords());
        }
    }

    private static double percentile(HistogramSnapshot snapshot, double percentile) {
        for (ValueAtPercentile value : snapshot.percentileValues()) {
            if (value.percentile() == percentile) {
                return value.value(TimeUnit.MILLISECONDS);
            }
        }
        return Double.NaN;
    }

    private static double mb(long bytes) {
        return bytes / Constants.BYTES_PER_MB;
    }

    @FunctionalInterface
    private interface MeasuredBody {
        Outcome run(Timer timer);
    }

    private record Outcome(long operations, long records) {
    }

    private record RangeQuery(String stationCode, Instant start, Instant end) {
    }

    private record DailyQuery(String stationCode, LocalDate date) {
    }
}
