package it.tesis.weatherbench.dto.ingestion;

import java.time.Instant;

import it.tesis.weatherbench.enumeration.StorageEngineType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionResponseDTO {

    private StorageEngineType engine;
    private String stationCode;
    private String source;
    private int samplesReceived;
    private Instant firstSample;
    private Instant lastSample;
    private double fetchTimeMs;
    private double storeTimeMs;
}
