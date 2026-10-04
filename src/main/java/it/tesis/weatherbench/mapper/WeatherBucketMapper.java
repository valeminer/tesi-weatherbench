package it.tesis.weatherbench.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.dto.measurement.DailyAggregateDto;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.model.bucket.ReadingDocument;
import it.tesis.weatherbench.model.bucket.projection.DailyStatsView;

@Component
public class WeatherBucketMapper {

    public ReadingDocument toReading(MeasurementDto dto) {
        return ReadingDocument.builder()
                .measurementId(WeatherMeasurementMapper.resolveId(dto))
                .measuredAt(dto.getMeasuredAt())
                .temperature(dto.getTemperature())
                .humidity(dto.getHumidity())
                .pressure(dto.getPressure())
                .windSpeed(dto.getWindSpeed())
                .windDirection(dto.getWindDirection())
                .precipitation(dto.getPrecipitation())
                .source(dto.getSource())
                .build();
    }

    public MeasurementDto toDto(String stationCode, ReadingDocument reading) {
        return MeasurementDto.builder()
                .id(reading.getMeasurementId())
                .stationCode(stationCode)
                .measuredAt(reading.getMeasuredAt())
                .temperature(reading.getTemperature())
                .humidity(reading.getHumidity())
                .pressure(reading.getPressure())
                .windSpeed(reading.getWindSpeed())
                .windDirection(reading.getWindDirection())
                .precipitation(reading.getPrecipitation())
                .source(reading.getSource())
                .build();
    }

    public DailyAggregateDto toDailyAggregate(String stationCode, LocalDate date, DailyStatsView view) {
        if (view == null || view.getSampleCount() == null || view.getSampleCount() == 0) {
            return DailyAggregateDto.empty(stationCode, date);
        }
        return DailyAggregateDto.builder()
                .stationCode(stationCode)
                .date(date)
                .sampleCount(view.getSampleCount())
                .avgTemperature(view.getAvgTemperature())
                .minTemperature(view.getMinTemperature())
                .maxTemperature(view.getMaxTemperature())
                .avgHumidity(view.getAvgHumidity())
                .avgPressure(view.getAvgPressure())
                .avgWindSpeed(view.getAvgWindSpeed())
                .maxWindSpeed(view.getMaxWindSpeed())
                .totalPrecipitation(view.getTotalPrecipitation())
                .build();
    }
}
