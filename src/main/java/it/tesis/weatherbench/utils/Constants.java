package it.tesis.weatherbench.utils;

public class Constants {

    private Constants() {}

    public static final String METRIC_BENCHMARK_LATENCY = "weatherbench.benchmark.latency";
    public static final String METRIC_TAG_ENGINE = "engine";
    public static final String METRIC_TAG_OPERATION = "operation";
    public static final String METRIC_TAG_RUN_ID = "runId";

    public static final String METRIC_JVM_MEMORY_USED = "jvm.memory.used";
    public static final String METRIC_JVM_GC_PAUSE = "jvm.gc.pause";

    public static final String SOURCE_SYNTHETIC = "SYNTHETIC";
    public static final String SOURCE_OPEN_METEO = "OPEN_METEO";

    public static final String BUCKET_GRANULARITY_DAILY = "DAILY";

    public static final String STATION_CODE_PREFIX = "ST";

    public static final double BYTES_PER_MB = 1024d * 1024d;
}
