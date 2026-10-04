package it.tesis.weatherbench.adapter.mongo;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AccumulatorOperators;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.measurement.DailyAggregateDto;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.mapper.WeatherBucketMapper;
import it.tesis.weatherbench.model.bucket.ReadingDocument;
import it.tesis.weatherbench.model.bucket.WeatherBucketDocument;
import it.tesis.weatherbench.model.bucket.projection.DailyStatsView;
import it.tesis.weatherbench.model.bucket.projection.UnwoundReadingView;
import it.tesis.weatherbench.port.StorageEnginePort;
import it.tesis.weatherbench.repository.mongo.WeatherBucketRepository;
import it.tesis.weatherbench.utils.Constants;
import it.tesis.weatherbench.utils.MeasurementIdCodec;

import lombok.extern.slf4j.Slf4j;

/**
 * Adapter MongoDB, modello documentale aggregato (Bucket Pattern giornaliero).
 *
 * <h2>Modello operativo stateless</h2>
 * A differenza dell'adapter JPA non esiste un contesto di persistenza:
 * <ul>
 *     <li>ogni chiamata a {@link MongoTemplate} converte il POJO in BSON, invia il comando e rilascia
 *     ogni riferimento: gli oggetti restano raggiungibili solo dal codice chiamante;</li>
 *     <li>nessuno snapshot, nessun dirty checking, nessun flush/clear: la memoria occupata dipende solo
 *     dalla dimensione del batch in corso di serializzazione;</li>
 *     <li>nessun proxy: letture nidificate materializzate direttamente dal documento;</li>
 *     <li>nessuna transazione (nessun MongoTransactionManager): ogni scrittura su singolo documento e'
 *     atomica per definizione, che e' esattamente la granularita' del bucket.</li>
 * </ul>
 */
@Slf4j
@Component
public class MongoStorageAdapter implements StorageEnginePort {

    private static final String READINGS_MEASUREMENT_ID = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_MEASUREMENT_ID;
    private static final String READINGS_MEASURED_AT = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_MEASURED_AT;
    private static final String READINGS_TEMPERATURE = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_TEMPERATURE;
    private static final String READINGS_HUMIDITY = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_HUMIDITY;
    private static final String READINGS_PRESSURE = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_PRESSURE;
    private static final String READINGS_WIND_SPEED = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_WIND_SPEED;
    private static final String READINGS_PRECIPITATION = WeatherBucketDocument.FIELD_READINGS + "." + ReadingDocument.FIELD_PRECIPITATION;

    private final MongoTemplate mongoTemplate;
    private final WeatherBucketRepository bucketRepository;
    private final WeatherBucketMapper mapper;
    private final int bulkSize;

    public MongoStorageAdapter(MongoTemplate mongoTemplate, WeatherBucketRepository bucketRepository,
            WeatherBucketMapper mapper, BenchmarkProperties benchmarkProperties) {
        this.mongoTemplate = mongoTemplate;
        this.bucketRepository = bucketRepository;
        this.mapper = mapper;
        this.bulkSize = benchmarkProperties.getMongoBulkSize();
    }

    @Override
    public StorageEngineType engineType() {
        return StorageEngineType.MONGO;
    }

    /**
     * Un comando updateOne con upsert per record: se il bucket del giorno non esiste viene creato
     * ($setOnInsert), la lettura viene aggiunta in coda all'array ($push) e il contatore incrementato ($inc).
     */
    @Override
    public void saveSingle(MeasurementDto dto) {
        BucketKey key = BucketKey.of(dto);
        mongoTemplate.upsert(bucketQuery(key), bucketUpsert(key, List.of(mapper.toReading(dto))), WeatherBucketDocument.class);
    }

    /**
     * Inserimento massivo con {@link BulkOperations} non ordinate.
     * <ol>
     *     <li>Le letture vengono raggruppate in memoria per bucket (stazione, giorno UTC): N letture
     *     diventano K operazioni, con K = numero di bucket distinti nel blocco (K &lt;&lt; N);</li>
     *     <li>ogni bucket produce un solo upsert con {@code $push: {readings: {$each: [...]}}};</li>
     *     <li>le operazioni sono inviate in un unico comando {@code bulkWrite} (UNORDERED: il server
     *     puo' parallelizzarle e un errore non interrompe le successive) ogni {@code bulkSize} operazioni.</li>
     * </ol>
     * Non serve alcun equivalente di flush()/clear(): dopo {@code execute()} nessun oggetto resta
     * referenziato dal driver.
     */
    @Override
    public void saveBatch(List<MeasurementDto> dtoList) {
        Map<BucketKey, List<ReadingDocument>> readingsByBucket = new LinkedHashMap<>();
        for (MeasurementDto dto : dtoList) {
            readingsByBucket.computeIfAbsent(BucketKey.of(dto), k -> new ArrayList<>()).add(mapper.toReading(dto));
        }

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, WeatherBucketDocument.class);
        int pending = 0;
        for (Map.Entry<BucketKey, List<ReadingDocument>> entry : readingsByBucket.entrySet()) {
            bulkOps.upsert(bucketQuery(entry.getKey()), bucketUpsert(entry.getKey(), entry.getValue()));
            if (++pending == bulkSize) {
                bulkOps.execute();
                bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, WeatherBucketDocument.class);
                pending = 0;
            }
        }
        if (pending > 0) {
            bulkOps.execute();
        }
    }

    /**
     * Lookup per {@code _id} del bucket (ricavato dall'id della misura) con proiezione posizionale
     * {@code readings.$}: il server restituisce solo l'elemento dell'array che soddisfa il filtro.
     */
    @Override
    public MeasurementDto findById(String id) {
        Query query = new Query(where(WeatherBucketDocument.FIELD_ID).is(MeasurementIdCodec.bucketIdOf(id))
                .and(READINGS_MEASUREMENT_ID).is(id));
        query.fields().include(WeatherBucketDocument.FIELD_STATION_CODE).position(WeatherBucketDocument.FIELD_READINGS, 1);

        WeatherBucketDocument bucket = mongoTemplate.findOne(query, WeatherBucketDocument.class);
        if (bucket == null || bucket.getReadings().isEmpty()) {
            return null;
        }
        return mapper.toDto(bucket.getStationCode(), bucket.getReadings().get(0));
    }

    /**
     * Aggregation pipeline eseguita dal server:
     * $match sui bucket (indice stationCode+bucketStart) -> $unwind delle letture ->
     * $match sul timestamp della lettura -> $sort cronologico.
     */
    @Override
    public List<MeasurementDto> findRange(String stationCode, Instant start, Instant end) {
        TypedAggregation<WeatherBucketDocument> aggregation = newAggregation(WeatherBucketDocument.class,
                match(where(WeatherBucketDocument.FIELD_STATION_CODE).is(stationCode)
                        .and(WeatherBucketDocument.FIELD_BUCKET_START).gte(start.truncatedTo(ChronoUnit.DAYS)).lt(end)),
                unwind(WeatherBucketDocument.FIELD_READINGS),
                match(where(READINGS_MEASURED_AT).gte(start).lt(end)),
                sort(Sort.Direction.ASC, READINGS_MEASURED_AT));

        return mongoTemplate.aggregate(aggregation, UnwoundReadingView.class).getMappedResults().stream()
                .map(view -> mapper.toDto(view.getStationCode(), view.getReadings()))
                .toList();
    }

    /**
     * Grazie al bucket giornaliero l'aggregazione legge un solo documento (lookup per _id) e calcola
     * le statistiche con operatori array nello stage $project, senza $unwind ne' $group.
     */
    @Override
    public DailyAggregateDto computeDailyStats(String stationCode, LocalDate date) {
        TypedAggregation<WeatherBucketDocument> aggregation = newAggregation(WeatherBucketDocument.class,
                match(where(WeatherBucketDocument.FIELD_ID).is(MeasurementIdCodec.bucketId(stationCode, date))),
                project(WeatherBucketDocument.FIELD_STATION_CODE)
                        .and(ArrayOperators.Size.lengthOfArray(WeatherBucketDocument.FIELD_READINGS)).as("sampleCount")
                        .and(AccumulatorOperators.Avg.avgOf(READINGS_TEMPERATURE)).as("avgTemperature")
                        .and(AccumulatorOperators.Min.minOf(READINGS_TEMPERATURE)).as("minTemperature")
                        .and(AccumulatorOperators.Max.maxOf(READINGS_TEMPERATURE)).as("maxTemperature")
                        .and(AccumulatorOperators.Avg.avgOf(READINGS_HUMIDITY)).as("avgHumidity")
                        .and(AccumulatorOperators.Avg.avgOf(READINGS_PRESSURE)).as("avgPressure")
                        .and(AccumulatorOperators.Avg.avgOf(READINGS_WIND_SPEED)).as("avgWindSpeed")
                        .and(AccumulatorOperators.Max.maxOf(READINGS_WIND_SPEED)).as("maxWindSpeed")
                        .and(AccumulatorOperators.Sum.sumOf(READINGS_PRECIPITATION)).as("totalPrecipitation"));

        DailyStatsView view = mongoTemplate.aggregate(aggregation, DailyStatsView.class).getUniqueMappedResult();
        return mapper.toDailyAggregate(stationCode, date, view);
    }

    @Override
    public long count() {
        TypedAggregation<WeatherBucketDocument> aggregation = newAggregation(WeatherBucketDocument.class,
                group().sum(WeatherBucketDocument.FIELD_COUNT).as("total"));
        Document result = mongoTemplate.aggregate(aggregation, Document.class).getUniqueMappedResult();
        return result == null ? 0L : ((Number) result.get("total")).longValue();
    }

    @Override
    public void purgeAll() {
        long buckets = bucketRepository.count();
        bucketRepository.deleteAll();
        log.info("MongoDB storage purged ({} buckets removed)", buckets);
    }

    private static Query bucketQuery(BucketKey key) {
        return new Query(where(WeatherBucketDocument.FIELD_ID).is(key.bucketId()));
    }

    private static Update bucketUpsert(BucketKey key, List<ReadingDocument> readings) {
        Instant bucketStart = key.day().atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Update()
                .setOnInsert(WeatherBucketDocument.FIELD_STATION_CODE, key.stationCode())
                .setOnInsert(WeatherBucketDocument.FIELD_BUCKET_START, bucketStart)
                .setOnInsert(WeatherBucketDocument.FIELD_BUCKET_END, bucketStart.plus(1, ChronoUnit.DAYS))
                .setOnInsert(WeatherBucketDocument.FIELD_GRANULARITY, Constants.BUCKET_GRANULARITY_DAILY)
                .inc(WeatherBucketDocument.FIELD_COUNT, readings.size())
                .push(WeatherBucketDocument.FIELD_READINGS).each(readings.toArray());
    }

    private record BucketKey(String stationCode, LocalDate day) {

        static BucketKey of(MeasurementDto dto) {
            return new BucketKey(dto.getStationCode(), LocalDate.ofInstant(dto.getMeasuredAt(), ZoneOffset.UTC));
        }

        String bucketId() {
            return MeasurementIdCodec.bucketId(stationCode, day);
        }
    }
}
