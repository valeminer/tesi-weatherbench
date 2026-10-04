package it.tesis.weatherbench.model.bucket.projection;

import org.springframework.data.annotation.Id;

import it.tesis.weatherbench.model.bucket.ReadingDocument;

import lombok.Getter;
import lombok.Setter;

/**
 * Forma di un bucket dopo lo stage {@code $unwind: "$readings"}: un documento per lettura,
 * in cui {@code readings} non e' piu' un array ma il singolo sotto-documento.
 */
@Getter
@Setter
public class UnwoundReadingView {

    @Id
    private String bucketId;

    private String stationCode;

    private ReadingDocument readings;
}
