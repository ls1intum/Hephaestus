package de.tum.cit.aet.hephaestus.integration.slack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Slack ts value type: exact microsecond comparisons, fail-closed on malformed input. */
@Tag("unit")
class SlackTsTest {

    @Test
    void ofInstant_rendersTheFixedWidthSlackFormat() {
        assertThat(SlackTs.ofInstant(Instant.ofEpochSecond(1720000000L, 123_456_000L))).isEqualTo("1720000000.123456");
        assertThat(SlackTs.ofInstant(Instant.ofEpochSecond(7L))).isEqualTo("0000000007.000000");
    }

    @Test
    void isAfter_isStrict_atMicrosecondPrecision() {
        Instant boundary = Instant.ofEpochSecond(1720000000L, 500_000_000L); // ....500000

        assertThat(SlackTs.isAfter("1720000000.500001", boundary)).isTrue();
        assertThat(SlackTs.isAfter("1720000000.500000", boundary)).isFalse(); // equality is NOT after
        assertThat(SlackTs.isAfter("1720000000.499999", boundary)).isFalse();
    }

    @ParameterizedTest(name = "malformed ts \"{0}\" fails closed")
    @CsvSource(value = { "''", "abc", "17200.0000001", "-1.000000", "1720000000.12345678" }, emptyValue = "''")
    void malformedTs_failsClosed(String ts) {
        assertThat(SlackTs.isAfter(ts, Instant.EPOCH)).isFalse();
        assertThat(SlackTs.toEpochMicros(ts)).isNull();
    }

    @Test
    void compare_ordersNumerically_andMaxPicksTheLater() {
        assertThat(SlackTs.compare("1720000000.000002", "1720000000.000010")).isNegative();
        assertThat(SlackTs.max("1720000000.000002", "1720000000.000010")).isEqualTo("1720000000.000010");
        // A short fractional part means padded micros ("12.5" = 500000µs), never a smaller number.
        assertThat(SlackTs.compare("12.5", "12.499999")).isPositive();
        // Unparseable sorts before everything, so a corrupt watermark can never suppress real messages.
        assertThat(SlackTs.compare(null, "0000000001.000000")).isNegative();
        assertThat(SlackTs.max(null, "0000000001.000000")).isEqualTo("0000000001.000000");
    }
}
