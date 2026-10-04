package it.tesis.weatherbench.conf;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import it.tesis.weatherbench.enumeration.StorageEngineType;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Consente path case-insensitive: /api/benchmark/jpa/... e /api/benchmark/MONGO/...
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, StorageEngineType.class, StorageEngineType::fromPath);
    }
}
