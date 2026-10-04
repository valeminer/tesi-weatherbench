package it.tesis.weatherbench.model.bucket.projection;

import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.Setter;

/**
 * Risultato dello stage {@code $project} con operatori array ($size, $avg, $min, $max, $sum)
 * applicati direttamente all'array {@code readings} del bucket, senza $unwind.
 */
@Getter
@Setter
public class DailyStatsView {

    @Id
    private String bucketId;

    private String stationCode;
    private Long sampleCount;
    private Double avgTemperature;
    private Double minTemperature;
    private Double maxTemperature;
    private Double avgHumidity;
    private Double avgPressure;
    private Double avgWindSpeed;
    private Double maxWindSpeed;
    private Double totalPrecipitation;
}
