package it.tesis.weatherbench.dto.openmeteo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Risposta di {@code GET /v1/forecast} con {@code timeformat=unixtime}: serie orarie colonnari
 * (un array per variabile, allineati per indice all'array {@code time}).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoResponseDTO {

    private Double latitude;
    private Double longitude;
    private Double elevation;

    @JsonProperty("utc_offset_seconds")
    private Integer utcOffsetSeconds;

    private Hourly hourly;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hourly {

        /** Epoch seconds UTC */
        private List<Long> time;

        @JsonProperty("temperature_2m")
        private List<Double> temperature;

        @JsonProperty("relative_humidity_2m")
        private List<Double> humidity;

        @JsonProperty("surface_pressure")
        private List<Double> pressure;

        @JsonProperty("wind_speed_10m")
        private List<Double> windSpeed;

        @JsonProperty("wind_direction_10m")
        private List<Double> windDirection;

        private List<Double> precipitation;
    }
}
