package it.tesis.weatherbench.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import it.tesis.weatherbench.exception.WrongInputParameterException;

class MeasurementIdCodecTest {

    @Test
    void encodeAndDecodeRoundTrip() {
        Instant instant = Instant.parse("2024-03-15T23:50:00Z");
        String id = MeasurementIdCodec.encode("ST0007", instant);

        assertThat(id).isEqualTo("ST0007_" + instant.getEpochSecond());
        assertThat(MeasurementIdCodec.stationCodeOf(id)).isEqualTo("ST0007");
        assertThat(MeasurementIdCodec.instantOf(id)).isEqualTo(instant);
        assertThat(MeasurementIdCodec.bucketIdOf(id)).isEqualTo("ST0007:2024-03-15");
    }

    @Test
    void stationCodeMayContainSeparator() {
        String id = MeasurementIdCodec.encode("ROMA_EUR", Instant.EPOCH);

        assertThat(MeasurementIdCodec.stationCodeOf(id)).isEqualTo("ROMA_EUR");
    }

    @Test
    void malformedIdIsRejected() {
        assertThatThrownBy(() -> MeasurementIdCodec.instantOf("ST0001_abc")).isInstanceOf(WrongInputParameterException.class);
        assertThatThrownBy(() -> MeasurementIdCodec.stationCodeOf("nounderscore")).isInstanceOf(WrongInputParameterException.class);
    }
}
