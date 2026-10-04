# WeatherBench

Strumento sperimentale per il confronto empirico tra **JPA/Hibernate su MySQL** e **MongoDB** nell'ingestione e aggregazione di serie temporali meteorologiche. Sviluppato come artefatto sperimentale della mia tesi triennale in Ingegneria Informatica: misura throughput, latenze (p50/p95/p99), consumo di heap JVM e attività del Garbage Collector a parita' di carico di lavoro.

## Architettura

Un'unica porta di persistenza (`StorageEnginePort`) implementata da due adapter:

| | `JpaStorageAdapter` | `MongoStorageAdapter` |
|---|---|---|
| Modello | Normalizzato: `weather_station` 1:N `weather_measurement` | Bucket pattern: un documento per stazione/giorno (`weather_buckets`) |
| Scrittura batch | `persist()` + `flush()`/`clear()` periodico | `BulkOperations` UNORDERED con upsert raggruppati |
| Aggregazione | `GROUP BY` JPQL su `Tuple` | Pipeline `$match` + `$project` con operatori su array |

I benchmark usano un dataset sintetico deterministico (`SplittableRandom` + seed fisso, fingerprint SHA-256) e piani di query deterministici: entrambi i motori eseguono le stesse operazioni sugli stessi dati. Una fonte secondaria (Open-Meteo API) permette l'ingestione di dati reali a scopo di validazione.

## Requisiti

- Java 21+
- Maven 3.8+
- MySQL 8.x su `localhost:3306` (database `weatherbench`, creato automaticamente)
- MongoDB su `localhost:27017` (senza autenticazione, per uso locale)

## Avvio

```bash
mvn clean compile spring-boot:run
```

Profili JVM per gli scenari sperimentali:

```bash
mvn spring-boot:run -P heap-constrained   # Xmx 512m
mvn spring-boot:run -P heap-large         # Xms/Xmx 4g
mvn spring-boot:run -P zgc                # Generational ZGC
```

Porte e credenziali configurabili via variabili d'ambiente (`APP_PORT`, `MYSQL_*`, `MONGO_*`) — vedi `src/main/resources/application.yml`.

Swagger UI: `http://localhost:8080/swagger-ui.html` — JMX per VisualVM: `localhost:9010`.

## API principali

```
POST /api/benchmark/{engine}/write-batch?records=N&mode=BATCH|SINGLE
GET  /api/benchmark/{engine}/read-range?datasetSize=N&iterations=K&windowHours=24
GET  /api/benchmark/{engine}/aggregate?datasetSize=N&iterations=K
GET  /api/benchmark/{engine}/read-by-id?datasetSize=N&iterations=K
GET  /api/benchmark/reports/csv
POST /api/ingestion/{engine}/open-meteo?stationCode=ROMA&latitude=41.9&longitude=12.5&pastDays=30
GET  /api/lifecycle/jpa/persistence-context   # demo stati entita' JPA
GET  /api/lifecycle/mongo/stateless           # demo modello stateless
```

`{engine}` = `jpa` | `mongo`. Ogni run produce un report JSON con throughput, percentili di latenza, statistiche heap e GC, e una riga in `logs/benchmark-results.csv`.

## Test

```bash
mvn clean test
```

## Stack

Spring Boot 3.3, Spring Data JPA + Hibernate 6.5, Spring Data MongoDB, MySQL 8, MongoDB, Micrometer + Actuator, Maven, JUnit 5.
