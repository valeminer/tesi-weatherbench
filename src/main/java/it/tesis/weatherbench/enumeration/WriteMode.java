package it.tesis.weatherbench.enumeration;

/**
 * BATCH: un campione di latenza per ogni chiamata a saveBatch() (blocco di chunk-size record).
 * SINGLE: un campione di latenza per ogni chiamata a saveSingle() (una transazione/un comando per record).
 */
public enum WriteMode {

    BATCH,
    SINGLE
}
