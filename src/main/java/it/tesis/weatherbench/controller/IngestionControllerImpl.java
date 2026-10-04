package it.tesis.weatherbench.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import it.tesis.weatherbench.dto.ingestion.IngestionResponseDTO;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.service.IngestionService;

@RestController
public class IngestionControllerImpl implements IngestionController {

    private final IngestionService ingestionService;

    public IngestionControllerImpl(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public ResponseEntity<IngestionResponseDTO> ingestOpenMeteo(StorageEngineType engine, String stationCode,
            double latitude, double longitude, int pastDays) {
        return ResponseEntity.ok(ingestionService.ingestOpenMeteo(engine, stationCode, latitude, longitude, pastDays));
    }
}
