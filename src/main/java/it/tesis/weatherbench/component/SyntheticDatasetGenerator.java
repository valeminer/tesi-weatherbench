package it.tesis.weatherbench.component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.benchmark.DatasetDescriptorDTO;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.utils.Constants;
import it.tesis.weatherbench.utils.MeasurementIdCodec;

/**
 * Generatore deterministico di dataset meteo sintetici (Capitolo 3 - replicabilita').
 * <p>
 * Proprieta' garantite:
 * <ul>
 *     <li><b>Determinismo</b>: stesso (seed, stations, interval, startInstant, records) =&gt; stessa
 *     sequenza di record bit-per-bit, su qualunque macchina/JVM ({@link SplittableRandom} ha un
 *     algoritmo specificato, al contrario di {@code Math.random()}); verificabile con il fingerprint.</li>
 *     <li><b>Prefisso stabile</b>: il dataset da 10.000 record coincide con i primi 10.000 record di
 *     quello da 100.000, quindi i carichi (1k, 10k, 100k, 500k) sono sottoinsiemi annidati.</li>
 *     <li><b>Distribuzione temporale regolare</b>: il record i appartiene alla stazione
 *     {@code i % stations} all'istante {@code start + (i / stations) * interval}; gli id sono unici.</li>
 * </ul>
 * I valori seguono un modello fisicamente plausibile: ciclo stagionale e diurno della temperatura,
 * umidita' anticorrelata, pressione con oscillazioni lente, vento semi-gaussiano, precipitazioni
 * intermittenti con distribuzione esponenziale.
 */
@Component
public class SyntheticDatasetGenerator {

    private static final double SECONDS_PER_YEAR = 365.25 * 86_400;
    private static final double SECONDS_PER_DAY = 86_400;
    private static final double TWO_PI = 2 * Math.PI;
    private static final long STATION_PROFILE_SALT = 0x9E3779B97F4A7C15L;

    private final BenchmarkProperties properties;

    public SyntheticDatasetGenerator(BenchmarkProperties properties) {
        this.properties = properties;
    }

    public List<MeasurementDto> generate(int records) {
        int stations = properties.getStations();
        long intervalSeconds = properties.getSamplingIntervalMinutes() * 60L;
        Instant start = properties.getStartInstant();

        StationProfile[] profiles = stationProfiles(stations);
        RandomGenerator random = new SplittableRandom(properties.getSeed());
        List<MeasurementDto> dataset = new ArrayList<>(records);

        for (int i = 0; i < records; i++) {
            StationProfile station = profiles[i % stations];
            Instant measuredAt = start.plusSeconds((long) (i / stations) * intervalSeconds);
            dataset.add(sample(station, measuredAt, random));
        }
        return dataset;
    }

    public DatasetDescriptorDTO describe(int records) {
        return describe(records, null);
    }

    public DatasetDescriptorDTO describe(int records, List<MeasurementDto> dataset) {
        int stations = properties.getStations();
        long steps = Math.max(1, (records + stations - 1) / stations);
        Instant first = properties.getStartInstant();
        return DatasetDescriptorDTO.builder()
                .records(records)
                .seed(properties.getSeed())
                .stations(Math.min(stations, records))
                .samplingIntervalMinutes(properties.getSamplingIntervalMinutes())
                .firstTimestamp(first)
                .lastTimestamp(first.plusSeconds((steps - 1) * properties.getSamplingIntervalMinutes() * 60L))
                .fingerprint(dataset == null ? null : fingerprint(dataset))
                .build();
    }

    /**
     * Id del record in posizione {@code index}, calcolato senza generare il dataset.
     */
    public String measurementId(int index) {
        int stations = properties.getStations();
        Instant measuredAt = properties.getStartInstant()
                .plusSeconds((long) (index / stations) * properties.getSamplingIntervalMinutes() * 60L);
        return MeasurementIdCodec.encode(stationCode(index % stations), measuredAt);
    }

    public static String stationCode(int stationIndex) {
        return Constants.STATION_CODE_PREFIX + String.format("%04d", stationIndex + 1);
    }

    /**
     * SHA-256 calcolato su una rappresentazione testuale canonica di ogni record.
     */
    public static String fingerprint(List<MeasurementDto> dataset) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder line = new StringBuilder(128);
            for (MeasurementDto dto : dataset) {
                line.setLength(0);
                line.append(dto.getId()).append('|').append(dto.getTemperature()).append('|').append(dto.getHumidity())
                        .append('|').append(dto.getPressure()).append('|').append(dto.getWindSpeed()).append('|')
                        .append(dto.getWindDirection()).append('|').append(dto.getPrecipitation()).append('\n');
                digest.update(line.toString().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private MeasurementDto sample(StationProfile station, Instant measuredAt, RandomGenerator random) {
        long epochSecond = measuredAt.getEpochSecond();
        double yearPhase = TWO_PI * (Math.floorMod(epochSecond, (long) SECONDS_PER_YEAR) / SECONDS_PER_YEAR);
        double dayPhase = TWO_PI * (Math.floorMod(epochSecond, (long) SECONDS_PER_DAY) / SECONDS_PER_DAY);

        // StrictMath: risultati bit-exact su qualunque piattaforma (Math puo' usare intrinseche CPU)
        // Minimo stagionale a meta' gennaio, minimo diurno alle ~04:00, massimo alle ~16:00 (UTC)
        double seasonal = -station.seasonalAmplitude() * StrictMath.cos(yearPhase - 0.26);
        double diurnal = -station.diurnalAmplitude() * StrictMath.cos(dayPhase - TWO_PI * 4 / 24);
        double temperature = station.baseTemperature() + seasonal + diurnal + random.nextGaussian() * 1.2;

        double humidity = clamp(68 - 1.8 * (seasonal + diurnal) + random.nextGaussian() * 6, 5, 100);
        double pressure = 1013.25 - station.elevation() / 8.3 + 6 * StrictMath.sin(yearPhase * 24) + random.nextGaussian() * 1.5;
        double windSpeed = Math.abs(station.meanWind() + random.nextGaussian() * 2.2);
        double windDirection = random.nextDouble(360);
        double precipitation = random.nextDouble() < 0.08 ? -StrictMath.log(1 - random.nextDouble()) * 1.3 : 0;

        return MeasurementDto.builder()
                .id(MeasurementIdCodec.encode(station.code(), measuredAt))
                .stationCode(station.code())
                .measuredAt(measuredAt)
                .temperature(round2(temperature))
                .humidity(round2(humidity))
                .pressure(round2(pressure))
                .windSpeed(round2(windSpeed))
                .windDirection(round2(windDirection))
                .precipitation(round2(precipitation))
                .source(Constants.SOURCE_SYNTHETIC)
                .build();
    }

    /**
     * Profilo climatico di ogni stazione, derivato da un generatore separato: aggiungere o rimuovere
     * record non altera i profili.
     */
    private StationProfile[] stationProfiles(int stations) {
        RandomGenerator random = new SplittableRandom(properties.getSeed() ^ STATION_PROFILE_SALT);
        StationProfile[] profiles = new StationProfile[stations];
        for (int s = 0; s < stations; s++) {
            profiles[s] = new StationProfile(stationCode(s),
                    random.nextDouble(4, 20),
                    random.nextDouble(6, 12),
                    random.nextDouble(3, 8),
                    random.nextDouble(0, 1500),
                    random.nextDouble(2, 7));
        }
        return profiles;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private record StationProfile(String code, double baseTemperature, double seasonalAmplitude,
            double diurnalAmplitude, double elevation, double meanWind) {
    }
}
