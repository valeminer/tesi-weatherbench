package it.tesis.weatherbench.model.bucket;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lettura nidificata nel bucket. Non e' un'entita': non ha ciclo di vita proprio, non ha proxy,
 * viene serializzata/deserializzata come sotto-documento BSON insieme al documento padre.
 * <p>
 * Nota: BSON Date ha precisione al millisecondo (MySQL DATETIME(6) al microsecondo); i dati del
 * benchmark sono allineati al minuto, quindi i due motori memorizzano valori identici.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingDocument {

    public static final String FIELD_MEASUREMENT_ID = "measurementId";
    public static final String FIELD_MEASURED_AT = "measuredAt";
    public static final String FIELD_TEMPERATURE = "temperature";
    public static final String FIELD_HUMIDITY = "humidity";
    public static final String FIELD_PRESSURE = "pressure";
    public static final String FIELD_WIND_SPEED = "windSpeed";
    public static final String FIELD_PRECIPITATION = "precipitation";

    @Field(FIELD_MEASUREMENT_ID)
    private String measurementId;

    @Field(FIELD_MEASURED_AT)
    private Instant measuredAt;

    @Field(FIELD_TEMPERATURE)
    private Double temperature;

    @Field(FIELD_HUMIDITY)
    private Double humidity;

    @Field(FIELD_PRESSURE)
    private Double pressure;

    @Field(FIELD_WIND_SPEED)
    private Double windSpeed;

    @Field("windDirection")
    private Double windDirection;

    @Field(FIELD_PRECIPITATION)
    private Double precipitation;

    @Field("source")
    private String source;
}
