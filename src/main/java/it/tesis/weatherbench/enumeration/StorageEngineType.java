package it.tesis.weatherbench.enumeration;

import java.util.Arrays;
import java.util.stream.Collectors;

import it.tesis.weatherbench.exception.WrongInputParameterException;

public enum StorageEngineType {

    JPA,
    MONGO;

    public static StorageEngineType fromPath(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new WrongInputParameterException("Unsupported storage engine '" + value
                        + "'. Allowed values: " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))));
    }
}
