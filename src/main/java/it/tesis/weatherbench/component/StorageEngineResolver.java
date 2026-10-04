package it.tesis.weatherbench.component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import it.tesis.weatherbench.enumeration.StorageEngineType;
import it.tesis.weatherbench.port.StorageEnginePort;

/**
 * Registro degli adapter disponibili: il layer applicativo seleziona il motore a runtime
 * conoscendo soltanto la porta {@link StorageEnginePort}.
 */
@Component
public class StorageEngineResolver {

    private final Map<StorageEngineType, StorageEnginePort> ports = new EnumMap<>(StorageEngineType.class);

    public StorageEngineResolver(List<StorageEnginePort> adapters) {
        adapters.forEach(adapter -> ports.put(adapter.engineType(), adapter));
    }

    public StorageEnginePort resolve(StorageEngineType engineType) {
        StorageEnginePort port = ports.get(engineType);
        if (port == null) {
            throw new IllegalStateException("No StorageEnginePort registered for " + engineType);
        }
        return port;
    }
}
