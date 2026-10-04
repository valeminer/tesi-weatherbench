package it.tesis.weatherbench.adapter.jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.measurement.DailyAggregateDto;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.mapper.WeatherMeasurementMapper;
import it.tesis.weatherbench.model.entity.WeatherStationEntity;
import it.tesis.weatherbench.port.StorageEnginePort;
import it.tesis.weatherbench.repository.jpa.WeatherMeasurementRepository;
import it.tesis.weatherbench.repository.jpa.WeatherStationRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter JPA/Hibernate verso MySQL, modello relazionale normalizzato (stazione 1:N misure).
 *
 * <h2>Persistence Context</h2>
 * L'{@link EntityManager} iniettato con {@link PersistenceContext} e' un proxy condiviso: ad ogni
 * transazione Spring apre un Persistence Context dedicato, cioe' una mappa
 * {@code (tipo, id) -> istanza gestita} che realizza:
 * <ul>
 *     <li><b>First-Level Cache / identity map</b>: all'interno della stessa transazione due
 *     {@code find()} con lo stesso id restituiscono la stessa istanza Java e la seconda non genera SQL;</li>
 *     <li><b>Dirty Checking</b>: per ogni entita' MANAGED Hibernate conserva uno snapshot dei valori
 *     caricati; al flush confronta campo per campo stato corrente e snapshot e genera gli UPDATE
 *     necessari senza alcuna chiamata esplicita a save();</li>
 *     <li><b>Write-behind</b>: persist() non esegue SQL ma accoda un'azione di INSERT nell'ActionQueue,
 *     eseguita al flush (esplicito, pre-query o al commit).</li>
 * </ul>
 * Il costo di questi servizi cresce linearmente con il numero di entita' gestite: memoria (istanza +
 * snapshot + entry nella mappa) e CPU (dirty checking di tutte le entita' ad ogni flush).
 * Una dimostrazione passo-passo e' esposta da {@code /api/lifecycle/jpa/persistence-context}.
 */
@Slf4j
@Component
public class JpaStorageAdapter implements StorageEnginePort {

    @PersistenceContext
    private EntityManager entityManager;

    private final WeatherStationRepository stationRepository;
    private final WeatherMeasurementRepository measurementRepository;
    private final WeatherMeasurementMapper mapper;
    private final int flushInterval;

    /** stationCode -> PK. Evita una SELECT per ogni misura: la stazione e' referenziata via proxy. */
    private final Map<String, Long> stationIdCache = new ConcurrentHashMap<>();

    public JpaStorageAdapter(WeatherStationRepository stationRepository,
            WeatherMeasurementRepository measurementRepository, WeatherMeasurementMapper mapper,
            BenchmarkProperties benchmarkProperties) {
        this.stationRepository = stationRepository;
        this.measurementRepository = measurementRepository;
        this.mapper = mapper;
        this.flushInterval = benchmarkProperties.getJpaFlushInterval();
    }

    @Override
    public StorageEngineType engineType() {
        return StorageEngineType.JPA;
    }

    /**
     * Una transazione per record: persist() rende l'entita' MANAGED (NEW -> MANAGED), l'INSERT viene
     * eseguito al commit (flush implicito), poi il Persistence Context viene chiuso e l'entita' diventa
     * DETACHED. E' lo scenario OLTP con il massimo overhead per record (apertura EM, commit, round-trip).
     */
    @Override
    @Transactional
    public void saveSingle(MeasurementDto dto) {
        entityManager.persist(mapper.toEntity(dto, resolveStationReference(dto.getStationCode())));
    }

    /**
     * Inserimento massivo in un'unica transazione con svuotamento periodico del Persistence Context.
     * <p>
     * Senza {@code flush()/clear()} ogni entita' persistita resterebbe referenziata dal Persistence
     * Context fino al commit: con 500.000 record l'heap conterrebbe 500.000 entita' + snapshot e ogni
     * flush automatico dovrebbe ispezionarle tutte (dirty checking O(n)), fino al rischio di
     * {@code OutOfMemoryError}. Ogni {@code flushInterval} record (allineato a
     * {@code hibernate.jdbc.batch_size}):
     * <ol>
     *     <li>{@code flush()}: l'ActionQueue viene svuotata, gli INSERT vengono inviati al DB in batch
     *     JDBC (riscritti in INSERT multi-riga da {@code rewriteBatchedStatements=true}); i dati sono
     *     scritti ma non ancora committati;</li>
     *     <li>{@code clear()}: tutte le entita' passano a DETACHED e diventano irraggiungibili, quindi
     *     collezionabili dal GC gia' nella young generation. L'occupazione dell'heap resta limitata a
     *     circa {@code flushInterval} entita' indipendentemente dalla dimensione del dataset.</li>
     * </ol>
     * Dopo clear() anche le stazioni sono DETACHED: la mappa locale di riferimenti viene svuotata e
     * i riferimenti vengono ricreati con {@code getReference()} (proxy non inizializzato, nessuna SELECT).
     */
    @Override
    @Transactional
    public void saveBatch(List<MeasurementDto> dtoList) {
        Map<String, WeatherStationEntity> stationReferences = new HashMap<>();
        int pending = 0;

        for (MeasurementDto dto : dtoList) {
            WeatherStationEntity station = stationReferences.computeIfAbsent(dto.getStationCode(),
                    this::resolveStationReference);
            entityManager.persist(mapper.toEntity(dto, station));

            if (++pending % flushInterval == 0) {
                entityManager.flush();
                entityManager.clear();
                stationReferences.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Transazione read-only: Spring imposta la sessione Hibernate in FlushMode.MANUAL e in modalita'
     * read-only, quindi le entita' caricate non hanno snapshot per il dirty checking (meno memoria).
     */
    @Override
    @Transactional(readOnly = true)
    public MeasurementDto findById(String id) {
        return measurementRepository.findWithStationById(id).map(mapper::toDto).orElse(null);
    }

    /**
     * Le entita' restituite vengono comunque registrate nel Persistence Context (identity map) fino
     * alla fine della transazione: e' il costo strutturale del modello a entita' gestite rispetto al
     * mapping diretto documento -> POJO di MongoDB.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MeasurementDto> findRange(String stationCode, Instant start, Instant end) {
        return measurementRepository.findRange(stationCode, start, end).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DailyAggregateDto computeDailyStats(String stationCode, LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Tuple tuple = measurementRepository.computeDailyStats(stationCode, start, start.plusSeconds(86_400));
        long sampleCount = tuple.get("sampleCount", Long.class);
        if (sampleCount == 0) {
            return DailyAggregateDto.empty(stationCode, date);
        }
        return DailyAggregateDto.builder()
                .stationCode(stationCode)
                .date(date)
                .sampleCount(sampleCount)
                .avgTemperature(tuple.get("avgTemperature", Double.class))
                .minTemperature(tuple.get("minTemperature", Double.class))
                .maxTemperature(tuple.get("maxTemperature", Double.class))
                .avgHumidity(tuple.get("avgHumidity", Double.class))
                .avgPressure(tuple.get("avgPressure", Double.class))
                .avgWindSpeed(tuple.get("avgWindSpeed", Double.class))
                .maxWindSpeed(tuple.get("maxWindSpeed", Double.class))
                .totalPrecipitation(tuple.get("totalPrecipitation", Double.class))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return measurementRepository.count();
    }

    @Override
    @Transactional
    public void purgeAll() {
        measurementRepository.truncate();
        int stations = stationRepository.deleteAllInBulk();
        entityManager.clear();
        stationIdCache.clear();
        log.info("JPA storage purged ({} stations removed)", stations);
    }

    /**
     * Restituisce un riferimento alla stazione senza caricarla: se l'entita' e' gia' MANAGED viene
     * restituita l'istanza dalla first-level cache, altrimenti un proxy Hibernate non inizializzato
     * che contiene solo la PK (sufficiente per valorizzare la FK station_id).
     */
    private WeatherStationEntity resolveStationReference(String stationCode) {
        Long stationId = stationIdCache.get(stationCode);
        if (stationId == null) {
            stationId = stationRepository.findIdByStationCode(stationCode).orElseGet(() -> createStation(stationCode));
            cacheAfterCommit(stationCode, stationId);
        }
        return entityManager.getReference(WeatherStationEntity.class, stationId);
    }

    private Long createStation(String stationCode) {
        WeatherStationEntity station = WeatherStationEntity.builder().stationCode(stationCode).name(stationCode).build();
        // IDENTITY: persist() esegue subito l'INSERT per ottenere la PK generata dal DB
        entityManager.persist(station);
        log.debug("Created station {} with id {}", stationCode, station.getId());
        return station.getId();
    }

    private void cacheAfterCommit(String stationCode, Long stationId) {
        stationIdCache.put(stationCode, stationId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        stationIdCache.remove(stationCode);
                    }
                }
            });
        }
    }
}
