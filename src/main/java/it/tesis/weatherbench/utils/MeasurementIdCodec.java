package it.tesis.weatherbench.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import it.tesis.weatherbench.exception.WrongInputParameterException;

/**
 * Identificativo naturale e deterministico di una misura: {@code <stationCode>_<epochSecond>}.
 * <p>
 * Lo stesso id e' usato da entrambi i motori: su MySQL e' la primary key (assegnata
 * dall'applicazione, quindi compatibile con il JDBC batching di Hibernate, a differenza di
 * IDENTITY); su MongoDB consente di ricavare l'id del bucket giornaliero senza indici aggiuntivi.
 */
public final class MeasurementIdCodec {

    private static final char SEPARATOR = '_';
    private static final char BUCKET_SEPARATOR = ':';

    private MeasurementIdCodec() {}

    public static String encode(String stationCode, Instant measuredAt) {
        return stationCode + SEPARATOR + measuredAt.getEpochSecond();
    }

    public static String stationCodeOf(String measurementId) {
        return measurementId.substring(0, separatorIndex(measurementId));
    }

    public static Instant instantOf(String measurementId) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(measurementId.substring(separatorIndex(measurementId) + 1)));
        } catch (NumberFormatException e) {
            throw new WrongInputParameterException("Malformed measurement id '" + measurementId + "'");
        }
    }

    /**
     * Id del bucket MongoDB giornaliero (UTC): {@code <stationCode>:<yyyy-MM-dd>}.
     */
    public static String bucketId(String stationCode, LocalDate day) {
        return stationCode + BUCKET_SEPARATOR + day;
    }

    public static String bucketId(String stationCode, Instant instant) {
        return bucketId(stationCode, LocalDate.ofInstant(instant, ZoneOffset.UTC));
    }

    public static String bucketIdOf(String measurementId) {
        return bucketId(stationCodeOf(measurementId), instantOf(measurementId));
    }

    private static int separatorIndex(String measurementId) {
        int index = measurementId == null ? -1 : measurementId.lastIndexOf(SEPARATOR);
        if (index <= 0 || index == measurementId.length() - 1) {
            throw new WrongInputParameterException("Malformed measurement id '" + measurementId + "'");
        }
        return index;
    }
}
