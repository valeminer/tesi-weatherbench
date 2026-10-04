package it.tesis.weatherbench.dto.benchmark;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Descrizione completa (e sufficiente a rigenerarlo) di un dataset sintetico.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDescriptorDTO {

    private int records;
    private long seed;
    private int stations;
    private int samplingIntervalMinutes;
    private Instant firstTimestamp;
    private Instant lastTimestamp;
    /** SHA-256 del contenuto: due run con lo stesso fingerprint hanno scritto dati identici */
    private String fingerprint;
}
