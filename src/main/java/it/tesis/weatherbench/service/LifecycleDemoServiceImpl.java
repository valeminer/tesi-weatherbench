package it.tesis.weatherbench.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.mapper.WeatherBucketMapper;
import it.tesis.weatherbench.mapper.WeatherMeasurementMapper;
import it.tesis.weatherbench.model.bucket.WeatherBucketDocument;
import it.tesis.weatherbench.model.entity.WeatherMeasurementEntity;
import it.tesis.weatherbench.model.entity.WeatherStationEntity;
import it.tesis.weatherbench.utils.Constants;
import it.tesis.weatherbench.utils.MeasurementIdCodec;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Dimostrazioni eseguibili del ciclo di vita delle entita' (Capitolo 2). Ogni passo registra
 * un'osservazione verificabile (stato dell'entita', numero di statement SQL, identita' delle istanze).
 * La demo JPA termina con rollback, la demo MongoDB rimuove il documento creato.
 */
@Slf4j
@Service
public class LifecycleDemoServiceImpl implements LifecycleDemoService {

    private static final String DEMO_STATION = "DEMO-LIFECYCLE";

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate transactionTemplate;
    private final WeatherMeasurementMapper measurementMapper;
    private final WeatherBucketMapper bucketMapper;
    private final MongoTemplate mongoTemplate;

    public LifecycleDemoServiceImpl(PlatformTransactionManager transactionManager,
            WeatherMeasurementMapper measurementMapper, WeatherBucketMapper bucketMapper, MongoTemplate mongoTemplate) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.measurementMapper = measurementMapper;
        this.bucketMapper = bucketMapper;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Object> jpaPersistenceContextDemo() {
        return transactionTemplate.execute(status -> {
            Map<String, Object> steps = new LinkedHashMap<>();
            Session session = entityManager.unwrap(Session.class);
            Statistics statistics = session.getSessionFactory().getStatistics();
            boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
            statistics.setStatisticsEnabled(true);
            try {
                WeatherStationEntity station = WeatherStationEntity.builder().stationCode(DEMO_STATION).name("Lifecycle demo").build();
                entityManager.persist(station);

                // 1. NEW (transient) -> MANAGED
                WeatherMeasurementEntity measurement = measurementMapper.toEntity(demoMeasurement(), station);
                steps.put("01_new_entity_is_managed", entityManager.contains(measurement));
                long statementsBefore = statistics.getPrepareStatementCount();
                entityManager.persist(measurement);
                steps.put("02_after_persist_is_managed", entityManager.contains(measurement));
                steps.put("03_sql_executed_by_persist (write-behind)", statistics.getPrepareStatementCount() - statementsBefore);
                steps.put("04_entities_in_persistence_context", session.getStatistics().getEntityCount());

                // 2. flush: l'ActionQueue viene eseguita
                statementsBefore = statistics.getPrepareStatementCount();
                entityManager.flush();
                steps.put("05_sql_executed_by_flush (INSERT)", statistics.getPrepareStatementCount() - statementsBefore);

                // 3. First-Level Cache: stessa istanza, nessuna SELECT
                statementsBefore = statistics.getPrepareStatementCount();
                WeatherMeasurementEntity first = entityManager.find(WeatherMeasurementEntity.class, measurement.getId());
                WeatherMeasurementEntity second = entityManager.find(WeatherMeasurementEntity.class, measurement.getId());
                steps.put("06_find_twice_returns_same_instance", first == measurement && second == measurement);
                steps.put("07_sql_executed_by_two_finds (first-level cache hit)", statistics.getPrepareStatementCount() - statementsBefore);

                // 4. clear(): tutte le entita' diventano DETACHED
                entityManager.clear();
                steps.put("08_after_clear_is_managed", entityManager.contains(measurement));
                statementsBefore = statistics.getPrepareStatementCount();
                WeatherMeasurementEntity reloaded = entityManager.find(WeatherMeasurementEntity.class, measurement.getId());
                steps.put("09_sql_executed_by_find_after_clear (SELECT)", statistics.getPrepareStatementCount() - statementsBefore);
                steps.put("10_reloaded_is_new_instance", reloaded != measurement);

                // 5. Dirty Checking: UPDATE generato senza alcuna chiamata esplicita
                long updatesBefore = statistics.getEntityUpdateCount();
                reloaded.setTemperature(reloaded.getTemperature() + 10);
                entityManager.flush();
                steps.put("11_updates_after_setter_and_flush (dirty checking)", statistics.getEntityUpdateCount() - updatesBefore);

                updatesBefore = statistics.getEntityUpdateCount();
                entityManager.flush();
                steps.put("12_updates_after_flush_without_changes", statistics.getEntityUpdateCount() - updatesBefore);

                // 6. DETACHED: le modifiche sono ignorate finche' l'entita' non viene riagganciata
                entityManager.detach(reloaded);
                reloaded.setTemperature(-99.0);
                updatesBefore = statistics.getEntityUpdateCount();
                entityManager.flush();
                steps.put("13_updates_after_modifying_detached_entity", statistics.getEntityUpdateCount() - updatesBefore);

                // 7. merge(): copia lo stato del detached su una nuova istanza MANAGED
                WeatherMeasurementEntity merged = entityManager.merge(reloaded);
                entityManager.flush();
                steps.put("14_merge_returns_different_managed_instance", merged != reloaded && entityManager.contains(merged));
                steps.put("15_updates_after_merge_and_flush", statistics.getEntityUpdateCount() - updatesBefore);

                // 8. Proxy LAZY: getReference() non esegue SQL finche' non si accede a un attributo
                entityManager.clear();
                statementsBefore = statistics.getPrepareStatementCount();
                WeatherStationEntity reference = entityManager.getReference(WeatherStationEntity.class, station.getId());
                steps.put("16_reference_runtime_class", reference.getClass().getName());
                steps.put("17_proxy_initialized_before_access", Hibernate.isInitialized(reference));
                steps.put("18_sql_executed_by_getReference", statistics.getPrepareStatementCount() - statementsBefore);
                reference.getName();
                steps.put("19_proxy_initialized_after_access", Hibernate.isInitialized(reference));
                steps.put("20_sql_executed_by_proxy_initialization", statistics.getPrepareStatementCount() - statementsBefore);
            } finally {
                statistics.setStatisticsEnabled(statisticsWereEnabled);
                status.setRollbackOnly();
            }
            steps.put("21_outcome", "transaction rolled back: no data persisted");
            return steps;
        });
    }

    @Override
    public Map<String, Object> mongoStatelessDemo() {
        Map<String, Object> steps = new LinkedHashMap<>();
        MeasurementDto dto = demoMeasurement();
        String bucketId = MeasurementIdCodec.bucketId(DEMO_STATION, dto.getMeasuredAt());
        Query byId = new Query(where(WeatherBucketDocument.FIELD_ID).is(bucketId));
        Instant bucketStart = LocalDate.ofInstant(dto.getMeasuredAt(), ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        WeatherBucketDocument document = WeatherBucketDocument.builder()
                .id(bucketId)
                .stationCode(DEMO_STATION)
                .bucketStart(bucketStart)
                .bucketEnd(bucketStart.plusSeconds(86_400))
                .granularity(Constants.BUCKET_GRANULARITY_DAILY)
                .count(1)
                .readings(List.of(bucketMapper.toReading(dto)))
                .build();
        try {
            mongoTemplate.remove(byId, WeatherBucketDocument.class);
            mongoTemplate.insert(document);
            steps.put("01_inserted_document_runtime_class", document.getClass().getName());

            // 1. Nessun dirty checking: modificare il POJO non ha effetti sul database
            document.getReadings().get(0).setTemperature(-99.0);
            WeatherBucketDocument reloaded = mongoTemplate.findOne(byId, WeatherBucketDocument.class);
            steps.put("02_pojo_temperature_after_local_change", document.getReadings().get(0).getTemperature());
            steps.put("03_db_temperature_after_local_change (unchanged)", reloaded.getReadings().get(0).getTemperature());

            // 2. Nessuna identity map: ogni lettura materializza una nuova istanza dal BSON
            WeatherBucketDocument reloadedAgain = mongoTemplate.findOne(byId, WeatherBucketDocument.class);
            steps.put("04_two_reads_return_same_instance", reloaded == reloadedAgain);
            steps.put("05_reloaded_runtime_class (no proxy)", reloaded.getClass().getName());

            // 3. Ogni modifica richiede un comando esplicito, atomico sul singolo documento
            mongoTemplate.updateFirst(byId, new Update().set("readings.0.temperature", -99.0), WeatherBucketDocument.class);
            WeatherBucketDocument afterUpdate = mongoTemplate.findOne(byId, WeatherBucketDocument.class);
            steps.put("06_db_temperature_after_explicit_update", afterUpdate.getReadings().get(0).getTemperature());
        } finally {
            mongoTemplate.remove(byId, WeatherBucketDocument.class);
        }
        steps.put("07_outcome", "demo document removed");
        return steps;
    }

    private static MeasurementDto demoMeasurement() {
        return MeasurementDto.builder()
                .stationCode(DEMO_STATION)
                .measuredAt(Instant.parse("2000-01-01T12:00:00Z"))
                .temperature(21.5)
                .humidity(55.0)
                .pressure(1013.0)
                .windSpeed(3.2)
                .windDirection(180.0)
                .precipitation(0.0)
                .source("DEMO")
                .build();
    }
}
