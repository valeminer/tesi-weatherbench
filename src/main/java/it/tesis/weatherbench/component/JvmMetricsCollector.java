package it.tesis.weatherbench.component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.utils.Constants;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Campionamento dello stato della JVM prima/dopo un benchmark, da tre sorgenti indipendenti:
 * <ul>
 *     <li>{@link Runtime}: {@code totalMemory - freeMemory} (heap occupato, vista grezza);</li>
 *     <li>Micrometer/Actuator: {@code jvm.memory.used{area=heap}} e {@code jvm.gc.pause};</li>
 *     <li>JMX platform MXBean: memory pool (picco) e GarbageCollectorMXBean (conteggio e tempo totale,
 *     inclusi i cicli concorrenti che jvm.gc.pause non registra come pause).</li>
 * </ul>
 * Le stesse grandezze sono visibili in tempo reale in VisualVM (tab Monitor / plugin VisualGC).
 */
@Slf4j
@Component
public class JvmMetricsCollector {

    private static final long GC_NOTIFICATION_SETTLE_MILLIS = 300;

    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final List<MemoryPoolMXBean> heapPools = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == MemoryType.HEAP)
            .toList();
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();

    public JvmMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public JvmSnapshot capture() {
        Runtime runtime = Runtime.getRuntime();
        long runtimeHeapUsed = runtime.totalMemory() - runtime.freeMemory();

        double actuatorHeapUsed = meterRegistry.find(Constants.METRIC_JVM_MEMORY_USED).tag("area", "heap").gauges()
                .stream().mapToDouble(Gauge::value).sum();

        long gcPauseCount = 0;
        double gcPauseMillis = 0;
        for (Timer timer : meterRegistry.find(Constants.METRIC_JVM_GC_PAUSE).timers()) {
            gcPauseCount += timer.count();
            gcPauseMillis += timer.totalTime(TimeUnit.MILLISECONDS);
        }

        long gcCollections = 0;
        long gcCollectionMillis = 0;
        for (GarbageCollectorMXBean collector : collectors) {
            gcCollections += Math.max(0, collector.getCollectionCount());
            gcCollectionMillis += Math.max(0, collector.getCollectionTime());
        }

        return new JvmSnapshot(System.nanoTime(), runtimeHeapUsed, memoryMXBean.getHeapMemoryUsage().getUsed(),
                (long) actuatorHeapUsed, runtime.totalMemory(), runtime.maxMemory(),
                gcPauseCount, gcPauseMillis, gcCollections, gcCollectionMillis);
    }

    /**
     * Richiede una full GC per partire da un heap contenente solo oggetti vivi. L'attesa successiva
     * lascia arrivare le notifiche JMX del GC (asincrone), che altrimenti verrebbero conteggiate in
     * {@code jvm.gc.pause} come attivita' del benchmark.
     */
    public void requestGc() {
        System.gc();
        try {
            Thread.sleep(GC_NOTIFICATION_SETTLE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void resetPeakHeapUsage() {
        heapPools.forEach(MemoryPoolMXBean::resetPeakUsage);
    }

    /**
     * Somma dei picchi dei singoli pool heap (eden, survivor, old) dall'ultimo reset: e' un limite
     * superiore dell'occupazione massima, perche' i picchi dei pool non sono necessariamente simultanei.
     */
    public long peakHeapUsedBytes() {
        return heapPools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
    }

    public List<String> garbageCollectorNames() {
        return collectors.stream().map(GarbageCollectorMXBean::getName).toList();
    }

    public record JvmSnapshot(
            long nanoTime,
            long runtimeHeapUsedBytes,
            long mxBeanHeapUsedBytes,
            long actuatorHeapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            long gcPauseCount,
            double gcPauseMillis,
            long gcCollectionCount,
            long gcCollectionMillis) {
    }
}
