package it.tesis.weatherbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot Application entrypoint.
 *
 */
@SpringBootApplication
@ConfigurationPropertiesScan("it.tesis.weatherbench.conf.properties")
public class WeatherBenchApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WeatherBenchApplication.class);
        application.run(args);
    }

}
