package it.tesis.weatherbench.model.bucket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Documento aggregato secondo il <b>Bucket Pattern</b>: un documento per stazione per giorno UTC,
 * con le letture nidificate nell'array {@code readings}.
 * <p>
 * Confronto con il modello relazionale:
 * <ul>
 *     <li>nessuna JOIN: stazione e letture sono co-locate nello stesso documento BSON;</li>
 *     <li>un'aggregazione giornaliera legge esattamente un documento (lookup per {@code _id});</li>
 *     <li>gli inserimenti sono upsert con {@code $push}: il numero di documenti cresce con i giorni,
 *     non con le letture (500.000 letture / 50 stazioni / 144 al giorno = circa 3.500 documenti).</li>
 * </ul>
 * Con campionamento a 10 minuti un bucket contiene 144 letture (circa 20 KB), ampiamente sotto
 * il limite di 16 MB per documento.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = WeatherBucketDocument.COLLECTION)
@CompoundIndex(name = "idx_bucket_station_start", def = "{'stationCode': 1, 'bucketStart': 1}")
public class WeatherBucketDocument {

    public static final String COLLECTION = "weather_buckets";

    public static final String FIELD_ID = "_id";
    public static final String FIELD_STATION_CODE = "stationCode";
    public static final String FIELD_BUCKET_START = "bucketStart";
    public static final String FIELD_BUCKET_END = "bucketEnd";
    public static final String FIELD_GRANULARITY = "granularity";
    public static final String FIELD_COUNT = "count";
    public static final String FIELD_READINGS = "readings";

    /** {@code <stationCode>:<yyyy-MM-dd>} */
    @Id
    private String id;

    @Field(FIELD_STATION_CODE)
    private String stationCode;

    @Field(FIELD_BUCKET_START)
    private Instant bucketStart;

    @Field(FIELD_BUCKET_END)
    private Instant bucketEnd;

    @Field(FIELD_GRANULARITY)
    private String granularity;

    @Field(FIELD_COUNT)
    private int count;

    @Builder.Default
    @Field(FIELD_READINGS)
    private List<ReadingDocument> readings = new ArrayList<>();
}
