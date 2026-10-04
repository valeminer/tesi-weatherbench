package it.tesis.weatherbench.dto.measurement;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Statistiche giornaliere (giorno UTC, intervallo semiaperto [00:00, 24:00)) di una stazione.
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DailyAggregateDto {

    private String stationCode;
    private LocalDate date;
    private long sampleCount;
    private Double avgTemperature;
    private Double minTemperature;
    private Double maxTemperature;
    private Double avgHumidity;
    private Double avgPressure;
    private Double avgWindSpeed;
    private Double maxWindSpeed;
    private Double totalPrecipitation;

    public static DailyAggregateDto empty(String stationCode, LocalDate date) {
        return DailyAggregateDto.builder().stationCode(stationCode).date(date).sampleCount(0).build();
    }
}
