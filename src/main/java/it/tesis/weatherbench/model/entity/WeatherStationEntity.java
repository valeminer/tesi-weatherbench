package it.tesis.weatherbench.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Anagrafica stazione (lato "uno" della relazione 1:N, tabella padre normalizzata).
 * <p>
 * Poche righe: la chiave surrogata IDENTITY non penalizza il batching perche' le stazioni vengono
 * create una sola volta e poi referenziate tramite {@code EntityManager.getReference()} (proxy).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weather_station",
        uniqueConstraints = @UniqueConstraint(name = "uk_station_code", columnNames = "station_code"))
public class WeatherStationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "station_code", nullable = false, length = 32)
    private String stationCode;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Lato inverso (non proprietario) della relazione: la FK vive in weather_measurement.station_id.
     * LAZY e mai popolato durante le scritture massive: aggiungere ogni misura a questa collezione
     * manterrebbe raggiungibili (e quindi non collezionabili dal GC) tutte le entita' inserite.
     */
    @Builder.Default
    @OneToMany(mappedBy = "station", fetch = FetchType.LAZY)
    private List<WeatherMeasurementEntity> measurements = new ArrayList<>();

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
