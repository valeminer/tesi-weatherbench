package it.tesis.weatherbench.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.dto.openmeteo.OpenMeteoResponseDTO;
import it.tesis.weatherbench.exception.ExternalServiceException;
import it.tesis.weatherbench.utils.Constants;
import it.tesis.weatherbench.utils.MeasurementIdCodec;

import lombok.extern.slf4j.Slf4j;

/**
 * Client sincrono (Spring {@link RestClient}) per le API pubbliche Open-Meteo, senza API key.
 */
@Slf4j
@Component
public class OpenMeteoClient {

    private static final String HOURLY_VARIABLES =
            "temperature_2m,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m,precipitation";

    private final RestClient openMeteoRestClient;

    public OpenMeteoClient(RestClient openMeteoRestClient) {
        this.openMeteoRestClient = openMeteoRestClient;
    }

    /**
     * Scarica le serie orarie degli ultimi {@code pastDays} giorni (max 92) e le converte in
     * {@link MeasurementDto}, scartando i campioni futuri (previsioni).
     */
    public List<MeasurementDto> fetchHourlyMeasurements(String stationCode, double latitude, double longitude, int pastDays) {
        OpenMeteoResponseDTO response;
        try {
            response = openMeteoRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("hourly", HOURLY_VARIABLES)
                            .queryParam("past_days", pastDays)
                            .queryParam("forecast_days", 1)
                            .queryParam("timeformat", "unixtime")
                            .queryParam("timezone", "GMT")
                            .queryParam("wind_speed_unit", "ms")
                            .build())
                    .retrieve()
                    .body(OpenMeteoResponseDTO.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Open-Meteo request failed: " + e.getMessage(), e);
        }
        return toMeasurements(stationCode, response);
    }

    private List<MeasurementDto> toMeasurements(String stationCode, OpenMeteoResponseDTO response) {
        if (response == null || response.getHourly() == null || response.getHourly().getTime() == null) {
            return List.of();
        }
        OpenMeteoResponseDTO.Hourly hourly = response.getHourly();
        Instant now = Instant.now();
        List<MeasurementDto> measurements = new ArrayList<>(hourly.getTime().size());

        for (int i = 0; i < hourly.getTime().size(); i++) {
            Instant measuredAt = Instant.ofEpochSecond(hourly.getTime().get(i));
            if (measuredAt.isAfter(now)) {
                continue;
            }
            measurements.add(MeasurementDto.builder()
                    .id(MeasurementIdCodec.encode(stationCode, measuredAt))
                    .stationCode(stationCode)
                    .measuredAt(measuredAt)
                    .temperature(valueAt(hourly.getTemperature(), i))
                    .humidity(valueAt(hourly.getHumidity(), i))
                    .pressure(valueAt(hourly.getPressure(), i))
                    .windSpeed(valueAt(hourly.getWindSpeed(), i))
                    .windDirection(valueAt(hourly.getWindDirection(), i))
                    .precipitation(valueAt(hourly.getPrecipitation(), i))
                    .source(Constants.SOURCE_OPEN_METEO)
                    .build());
        }
        log.info("Open-Meteo: {} hourly samples received for station {} ({}, {})",
                measurements.size(), stationCode, response.getLatitude(), response.getLongitude());
        return measurements;
    }

    private static Double valueAt(List<Double> series, int index) {
        return series != null && index < series.size() ? series.get(index) : null;
    }
}
