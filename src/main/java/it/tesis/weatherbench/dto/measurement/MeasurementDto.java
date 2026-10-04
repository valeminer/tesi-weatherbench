package it.tesis.weatherbench.dto.measurement;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * DTO neutrale rispetto al motore di persistenza: e' l'unico tipo che attraversa lo {@code StorageEnginePort}.
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementDto {

    private String id;

    @NotBlank
    private String stationCode;

    @NotNull
    private Instant measuredAt;

    private Double temperature;
    private Double humidity;
    private Double pressure;
    private Double windSpeed;
    private Double windDirection;
    private Double precipitation;

    private String source;
}
