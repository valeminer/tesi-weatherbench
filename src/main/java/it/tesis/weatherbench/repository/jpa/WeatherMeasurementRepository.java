package it.tesis.weatherbench.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.tesis.weatherbench.model.entity.WeatherMeasurementEntity;

import jakarta.persistence.Tuple;

@Repository
public interface WeatherMeasurementRepository extends JpaRepository<WeatherMeasurementEntity, String> {

    /**
     * JOIN FETCH: la stazione viene caricata nella stessa query, evitando l'inizializzazione
     * del proxy LAZY (e la relativa SELECT aggiuntiva) durante il mapping verso il DTO.
     */
    @Query("select m from WeatherMeasurementEntity m join fetch m.station where m.id = :id")
    Optional<WeatherMeasurementEntity> findWithStationById(String id);

    /**
     * Intervallo semiaperto [start, end). Sfrutta l'indice composto (station_id, measured_at).
     */
    @Query("""
            select m from WeatherMeasurementEntity m join fetch m.station s
            where s.stationCode = :stationCode and m.measuredAt >= :start and m.measuredAt < :end
            order by m.measuredAt
            """)
    List<WeatherMeasurementEntity> findRange(String stationCode, Instant start, Instant end);

    /**
     * Aggregazione eseguita interamente dal DBMS (GROUP BY implicito): nessuna entita' viene
     * materializzata ne' registrata nel persistence context, si ottiene una sola tupla scalare.
     */
    @Query("""
            select count(m) as sampleCount,
                   avg(m.temperature) as avgTemperature, min(m.temperature) as minTemperature,
                   max(m.temperature) as maxTemperature, avg(m.humidity) as avgHumidity,
                   avg(m.pressure) as avgPressure, avg(m.windSpeed) as avgWindSpeed,
                   max(m.windSpeed) as maxWindSpeed, sum(m.precipitation) as totalPrecipitation
            from WeatherMeasurementEntity m
            where m.station.stationCode = :stationCode and m.measuredAt >= :start and m.measuredAt < :end
            """)
    Tuple computeDailyStats(String stationCode, Instant start, Instant end);

    @Modifying
    @Query(value = "TRUNCATE TABLE weather_measurement", nativeQuery = true)
    void truncate();
}
