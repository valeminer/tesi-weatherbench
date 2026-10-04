package it.tesis.weatherbench.model.entity;

import java.time.Instant;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singola lettura (lato "molti", tabella figlia normalizzata).
 * <p>
 * Primary key naturale assegnata dall'applicazione ({@code <stationCode>_<epochSecond>}):
 * <ul>
 *     <li>con IDENTITY Hibernate sarebbe costretto a eseguire subito ogni INSERT per conoscere l'id,
 *     disabilitando di fatto il JDBC batching;</li>
 *     <li>con id assegnato gli INSERT restano accodati nell'ActionQueue fino al flush() e vengono
 *     inviati in batch da {@code hibernate.jdbc.batch_size} (riscritti in INSERT multi-riga dal
 *     driver MySQL grazie a {@code rewriteBatchedStatements=true}).</li>
 * </ul>
 * {@link Persistable} evita che un eventuale {@code repository.save()} esegua una SELECT + merge
 * per capire se l'entita' e' nuova.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weather_measurement", indexes = {
        @Index(name = "idx_measurement_station_time", columnList = "station_id, measured_at"),
        @Index(name = "idx_measurement_time", columnList = "measured_at")
})
public class WeatherMeasurementEntity implements Persistable<String> {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /**
     * Lato proprietario della relazione: genera la colonna FK station_id.
     * LAZY: caricare una misura non comporta la SELECT della stazione (viene creato un proxy).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false, foreignKey = @ForeignKey(name = "fk_measurement_station"))
    private WeatherStationEntity station;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "pressure")
    private Double pressure;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "wind_direction")
    private Double windDirection;

    @Column(name = "precipitation")
    private Double precipitation;

    @Column(name = "source", length = 16)
    private String source;

    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
