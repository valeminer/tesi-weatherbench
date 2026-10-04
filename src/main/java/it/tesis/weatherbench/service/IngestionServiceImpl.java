package it.tesis.weatherbench.service;

import java.util.List;

import org.springframework.stereotype.Service;

import it.tesis.weatherbench.client.OpenMeteoClient;
import it.tesis.weatherbench.component.StorageEngineResolver;
import it.tesis.weatherbench.dto.ingestion.IngestionResponseDTO;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.utils.Constants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IngestionServiceImpl implements IngestionService {

    private final OpenMeteoClient openMeteoClient;
    private final StorageEngineResolver engineResolver;

    public IngestionServiceImpl(OpenMeteoClient openMeteoClient, StorageEngineResolver engineResolver) {
        this.openMeteoClient = openMeteoClient;
        this.engineResolver = engineResolver;
    }

    @Override
    public IngestionResponseDTO ingestOpenMeteo(StorageEngineType engine, String stationCode, double latitude,
            double longitude, int pastDays) {
        long fetchStart = System.nanoTime();
        List<MeasurementDto> measurements = openMeteoClient.fetchHourlyMeasurements(stationCode, latitude, longitude, pastDays);
        long storeStart = System.nanoTime();
        if (!measurements.isEmpty()) {
            engineResolver.resolve(engine).saveBatch(measurements);
        }
        long end = System.nanoTime();
        log.info("Open-Meteo ingestion completed: engine={}, station={}, samples={}", engine, stationCode, measurements.size());

        return IngestionResponseDTO.builder()
                .engine(engine)
                .stationCode(stationCode)
                .source(Constants.SOURCE_OPEN_METEO)
                .samplesReceived(measurements.size())
                .firstSample(measurements.isEmpty() ? null : measurements.get(0).getMeasuredAt())
                .lastSample(measurements.isEmpty() ? null : measurements.get(measurements.size() - 1).getMeasuredAt())
                .fetchTimeMs((storeStart - fetchStart) / 1_000_000d)
                .storeTimeMs((end - storeStart) / 1_000_000d)
                .build();
    }
}
