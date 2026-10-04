package it.tesis.weatherbench.service;

import it.tesis.weatherbench.dto.ingestion.IngestionResponseDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;

public interface IngestionService {

    IngestionResponseDTO ingestOpenMeteo(StorageEngineType engine, String stationCode, double latitude, double longitude,
            int pastDays);
}
