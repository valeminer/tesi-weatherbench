package it.tesis.weatherbench.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import it.tesis.weatherbench.dto.measurement.DailyAggregateDto;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;
import it.tesis.weatherbench.enumeration.StorageEngineType;

/**
 * Porta di uscita (Architettura Esagonale): il dominio e il layer di benchmark dipendono solo da
 * questa astrazione, mai da JPA o da MongoDB. Ogni motore fornisce un adapter che la implementa,
 * rendendo i due backend intercambiabili a parita' di carico e di codice chiamante.
 * <p>
 * Semantica comune agli adapter:
 * <ul>
 *     <li>gli intervalli temporali sono semiaperti [start, end);</li>
 *     <li>i giorni sono intesi in UTC;</li>
 *     <li>se {@code dto.id} e' nullo viene calcolato con {@code MeasurementIdCodec}.</li>
 * </ul>
 */
public interface StorageEnginePort {

    StorageEngineType engineType();

    void saveSingle(MeasurementDto dto);

    void saveBatch(List<MeasurementDto> dtoList);

    /**
     * @return la misura, oppure {@code null} se non presente
     */
    MeasurementDto findById(String id);

    List<MeasurementDto> findRange(String stationCode, Instant start, Instant end);

    DailyAggregateDto computeDailyStats(String stationCode, LocalDate date);

    /**
     * Numero di misure memorizzate (verifica di coerenza dopo un benchmark di scrittura).
     */
    long count();

    /**
     * Svuota lo storage per ripartire da condizioni iniziali identiche fra un'esecuzione e l'altra.
     */
    void purgeAll();
}
