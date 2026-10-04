package it.tesis.weatherbench.mapper;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.model.entity.WeatherMeasurementEntity;
import it.tesis.weatherbench.model.entity.WeatherStationEntity;
import it.tesis.weatherbench.utils.MeasurementIdCodec;

@Component
public class WeatherMeasurementMapper {

    public WeatherMeasurementEntity toEntity(MeasurementDto dto, WeatherStationEntity station) {
        return WeatherMeasurementEntity.builder()
                .id(resolveId(dto))
                .station(station)
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

    /**
     * Richiede che la stazione sia gia' inizializzata (JOIN FETCH), altrimenti l'accesso a
     * {@code getStationCode()} provocherebbe l'inizializzazione del proxy LAZY.
     */
    public MeasurementDto toDto(WeatherMeasurementEntity entity) {
        return MeasurementDto.builder()
                .id(entity.getId())
                .stationCode(entity.getStation().getStationCode())
                .measuredAt(entity.getMeasuredAt())
                .temperature(entity.getTemperature())
                .humidity(entity.getHumidity())
                .pressure(entity.getPressure())
                .windSpeed(entity.getWindSpeed())
                .windDirection(entity.getWindDirection())
                .precipitation(entity.getPrecipitation())
                .source(entity.getSource())
                .build();
    }

    public static String resolveId(MeasurementDto dto) {
        return dto.getId() != null ? dto.getId() : MeasurementIdCodec.encode(dto.getStationCode(), dto.getMeasuredAt());
    }
}
