package it.tesis.weatherbench.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import it.tesis.weatherbench.conf.properties.BenchmarkProperties;
import it.tesis.weatherbench.dto.benchmark.DatasetDescriptorDTO;
import it.tesis.weatherbench.dto.measurement.MeasurementDto;

class SyntheticDatasetGeneratorTest {

    private final SyntheticDatasetGenerator generator = new SyntheticDatasetGenerator(new BenchmarkProperties());

    @Test
    void sameSeedProducesIdenticalDataset() {
        List<MeasurementDto> first = generator.generate(5_000);
        List<MeasurementDto> second = generator.generate(5_000);

        assertThat(SyntheticDatasetGenerator.fingerprint(first)).isEqualTo(SyntheticDatasetGenerator.fingerprint(second));
    }

    @Test
    void smallerDatasetIsPrefixOfLargerOne() {
        List<MeasurementDto> small = generator.generate(1_000);
        List<MeasurementDto> large = generator.generate(10_000);

        assertThat(SyntheticDatasetGenerator.fingerprint(large.subList(0, 1_000)))
                .isEqualTo(SyntheticDatasetGenerator.fingerprint(small));
    }

    @Test
    void differentSeedProducesDifferentDataset() {
        BenchmarkProperties otherSeed = new BenchmarkProperties();
        otherSeed.setSeed(7L);

        assertThat(SyntheticDatasetGenerator.fingerprint(new SyntheticDatasetGenerator(otherSeed).generate(1_000)))
                .isNotEqualTo(SyntheticDatasetGenerator.fingerprint(generator.generate(1_000)));
    }

    @Test
    void idsAreUniqueAndMatchIndexFormula() {
        List<MeasurementDto> dataset = generator.generate(10_000);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < dataset.size(); i++) {
            assertThat(dataset.get(i).getId()).isEqualTo(generator.measurementId(i));
            ids.add(dataset.get(i).getId());
        }
        assertThat(ids).hasSize(dataset.size());
    }

    @Test
    void descriptorCoversDatasetTimeRange() {
        List<MeasurementDto> dataset = generator.generate(1_000);
        DatasetDescriptorDTO descriptor = generator.describe(1_000, dataset);

        assertThat(descriptor.getFirstTimestamp()).isEqualTo(dataset.get(0).getMeasuredAt());
        assertThat(descriptor.getLastTimestamp()).isEqualTo(dataset.get(dataset.size() - 1).getMeasuredAt());
        assertThat(descriptor.getStations()).isEqualTo(50);
    }

    @Test
    void valuesArePhysicallyPlausible() {
        assertThat(generator.generate(20_000)).allSatisfy(dto -> {
            assertThat(dto.getTemperature()).isBetween(-40.0, 50.0);
            assertThat(dto.getHumidity()).isBetween(5.0, 100.0);
            assertThat(dto.getWindSpeed()).isGreaterThanOrEqualTo(0.0);
            assertThat(dto.getWindDirection()).isBetween(0.0, 360.0);
            assertThat(dto.getPrecipitation()).isGreaterThanOrEqualTo(0.0);
        });
    }
}
