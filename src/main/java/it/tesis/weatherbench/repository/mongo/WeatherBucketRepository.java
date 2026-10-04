package it.tesis.weatherbench.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import it.tesis.weatherbench.model.bucket.WeatherBucketDocument;

/**
 * Repository derivato per le operazioni CRUD semplici. Le operazioni massive e le aggregazioni
 * usano direttamente {@code MongoTemplate} nell'adapter (bulkOps, aggregation pipeline).
 */
@Repository
public interface WeatherBucketRepository extends MongoRepository<WeatherBucketDocument, String> {
}
